/*
 * Copyright (C) 2019 by Saverio Giallorenzo <saverio.giallorenzo@gmail.com>
 * Copyright (C) 2019 by Fabrizio Montesi <famontesi@gmail.com>
 * Copyright (C) 2019 by Marco Peressotti <marco.peressotti@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package choral.types;

import choral.ast.Node;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static choral.types.Modifier.*;
import static choral.types.ModifierUtils.assertLegalModifiers;

/** @see HigherDataType */
public final class HigherInterface extends HigherClassOrInterface implements Interface {

	public HigherInterface(
			Package declarationContext,
			EnumSet< Modifier > modifiers,
			String identifier,
			List< World > worldsParameters,
			List< HigherTypeParameter > typeParameters
	) {
		super( declarationContext,
				setImplicitModifiers( modifiers ),
				identifier,
				worldsParameters,
				typeParameters );
	}

	public HigherInterface(
			Package declarationContext,
			EnumSet< Modifier > modifiers,
			String identifier,
			List< World > worldsParameters,
			List< HigherTypeParameter > typeParameters,
			Node sourceCode
	) {
		super( declarationContext,
				setImplicitModifiers( modifiers ),
				identifier,
				worldsParameters,
				typeParameters,
				sourceCode );
	}

	@Override
	public Variety variety() {
		return Variety.INTERFACE;
	}

	private static final EnumSet< Modifier > legalModifiers = EnumSet.of( PUBLIC, ABSTRACT,
			STATIC );

	@Override
	protected void assertModifiers( EnumSet< Modifier > modifiers ) {
		assertLegalModifiers( legalModifiers, modifiers, "for interfaces" );
		super.assertModifiers( modifiers );
	}

	private static EnumSet< Modifier > setImplicitModifiers( EnumSet< Modifier > modifiers ) {
		modifiers.add( ABSTRACT );
		return modifiers;
	}

	@Override
	public GroundInterface applyTo( List< ? extends World > args ) {
		return applyTo( args,
				typeParameters.stream().map( HigherTypeParameter::getRawType ).collect(
						Collectors.toList() ) );
	}

	@Override
	public GroundInterface applyTo(
			List< ? extends World > worldArgs, List< ? extends HigherReferenceType > typeArgs
	) {
		return innerType().applySubstitution( getApplicationSubstitution( worldArgs, typeArgs ) );
	}

	private final Definition innerType = new Definition();

	@Override
	public Definition innerType() {
		return innerType;
	}

	public void addImplicitMethodModifiers( EnumSet< Modifier > modifiers ) {
		if (!modifiers.contains(DEFAULT) && !modifiers.contains(STATIC)) modifiers.add( ABSTRACT );
		modifiers.add( PUBLIC );
	}

	public final class Definition extends HigherClassOrInterface.Definition
			implements GroundInterface {

		private Definition() {
		}

		@Override
		public HigherInterface typeConstructor() {
			return HigherInterface.this;
		}

		private final HashMap< Substitution, GroundInterface > alphaIndex = new HashMap<>();

		@Override
		public GroundInterface applySubstitution( Substitution substitution ) {
			GroundInterface result = alphaIndex.get( substitution );
			if( result == null ) {
				result = new Proxy( substitution );
				alphaIndex.put( substitution, result );
			}
			return result;
		}

		@Override
		public Stream< ? extends Member.Field > fields() {
			return Stream.concat( declaredFields(),
					extendedInterfaces().flatMap( x -> x.fields() ) );
		}

		@Override
		public Stream< ? extends Member.HigherMethod > methods() {
			return Stream.concat( declaredMethods(),
					extendedInterfaces().flatMap( x -> x.methods() ) );
		}

		@Override
		public void addField( Member.Field field ) {
			throw new UnsupportedOperationException( "interfaces cannot have fields" );
		}

		@Override
		public void addMethod( Member.HigherMethod method ) {
			assert ( method.isPublic() && method.isAbstract() || method.isDefault() || method.isStatic() )
				: "'" + method.identifier() + "' modifiers : " + method.modifiers();
			super.addMethod( method );
		}

		private boolean interfaceFinalised = false;

		@Override
		public final boolean isInterfaceFinalised() {
			return interfaceFinalised;
		}

		@Override
		public void finaliseInterface() {
			assert (isInheritanceFinalised() && extendedClassesOrInterfaces()
					.allMatch(GroundReferenceType::isInterfaceFinalised));
			if (isInterfaceFinalised()) {
				return;
			}

			//////// COMPUTE INHERITED FIELDS

			extendedClassesOrInterfaces().flatMap( GroundReferenceType::fields )
					.filter( x -> x.isAccessibleFrom( this )
							&& declaredFields().noneMatch( y -> x.identifier().equals( y.identifier() ) ) )
					// The same public static final field could be inherited from multiple
					// interfaces, but we only want to inherit it once.
					.distinct()
					.forEach( inheritedFields::add );


			//////// COMPUTE INHERITED METHODS

			// Precompute the set of methods defined in direct superclasses and interfaces.
			var allAncestorMethods = extendedClassesOrInterfaces()
					.flatMap( GroundReferenceType::methods )
					.distinct()
					.toList();

			// Precompute the set of ancestor methods that are overridden by some other ancestor.
			Set< Member.HigherMethod > overriddenByAnother =
					Collections.newSetFromMap( new IdentityHashMap<>() );
			for ( Member.HigherMethod m : allAncestorMethods ) {
				for ( Member.HigherMethod m2 : allAncestorMethods ) {
					if ( m2.equals( m ) ) continue;  // TODO: Implement equality for proxy callables
					if ( m2.declarationContext().isEquivalentTo( m.declarationContext() ) ) continue;
					if ( m2.declarationContext().overrides( m2, m ) ) {
						overriddenByAnother.add( m );
						break; // m is already marked; no further m2 needed
					}
				}
			}

			// (JLS 9.4.1) An interface I inherits from its direct superinterfaces all abstract and
			// default methods m for which all of the following are true:
			// • m is a member of a direct superinterface, J, of I.
			// • No method declared in I has a signature that is a subsignature (§8.4.2) of the
			// signature of m.
			// • There exists no method m' that is a member of a direct superinterface, J', of I
			// (m distinct from m', J distinct from J'), such that m' overrides from J' the
			// declaration of the method m.
			allAncestorMethods.stream()
					.filter( m -> m.isAbstract() || m.isDefault() )
					.filter( m -> m.isAccessibleFrom( this ) )
					.filter( m -> declaredMethods().noneMatch( x -> x.isSubSignatureOf( m ) ) )
					.filter( m -> !overriddenByAnother.contains( m ) )
					.forEach( inheritedMethods::add );

			// TODO: If an interface I declares a static method m, and the signature of m is a
			//  subsignature of an instance method m' in a superinterface of I, and m' would
			//  otherwise be accessible to code in I, then a compile-time error occurs.


			interfaceFinalised = true;
		}

		@Override
		public boolean overrides(Member.HigherMethod m1, Member.HigherMethod m2) {
			// (JLS 9.4.1.1) An instance method m1, declared in or inherited by an interface I,
			// overrides from I another instance method, m2, declared in interface J, iff both of
			// the following are true:
			// • I is a subinterface of J.
			// • The signature of m1 is a subsignature (§8.4.2) of the signature of m2.
			return isSubtypeOf( m2.declarationContext() ) && m1.isSubSignatureOf( m2 );
		}

	}

	/** @see HigherDataType.Proxy */
	private final class Proxy extends HigherClassOrInterface.Proxy implements GroundInterface {

		private Proxy( Substitution substitution ) {
			super( substitution );
		}

		@Override
		public HigherInterface typeConstructor() {
			return HigherInterface.this;
		}

		@Override
		protected Definition definition() {
			return typeConstructor().innerType();
		}

		@Override
		public GroundInterface applySubstitution( Substitution substitution ) {
			return definition().applySubstitution( substitution().andThen( substitution ) );
		}

		@Override
		public boolean overrides(Member.HigherMethod m1, Member.HigherMethod m2) {
			return definition().overrides( m1, m2 ); // TODO Apply substitution in reverse
		}

	}

}
