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
import choral.exceptions.StaticVerificationException;
import choral.types.Member.HigherConstructor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static choral.types.Modifier.PUBLIC;

/** @see HigherDataType */
public class HigherClass extends HigherClassOrInterface implements Class {

	public HigherClass(
			Package declarationContext,
			EnumSet< Modifier > modifiers,
			String identifier,
			List< World > worldsParameters,
			List< HigherTypeParameter > typeParameters
	) {
		super( declarationContext, modifiers, identifier, worldsParameters, typeParameters );
	}

	public HigherClass(
			Package declarationContext,
			EnumSet< Modifier > modifiers,
			String identifier,
			List< World > worldsParameters,
			List< HigherTypeParameter > typeParameters,
			Node sourceCode
	) {
		super( declarationContext, modifiers, identifier, worldsParameters, typeParameters,
				sourceCode );
	}

	HigherClass(
			Package declarationContext,
			EnumSet< Modifier > modifiers,
			String identifier,
			List< World > worldsParameters,
			List< HigherTypeParameter > typeParameters,
			boolean registerWithDeclarationContext
	) {
		super( declarationContext, modifiers, identifier, worldsParameters, typeParameters, null,
				registerWithDeclarationContext );
	}

	@Override
	public Variety variety() {
		return Variety.CLASS;
	}

	@Override
	public boolean isBoxedType() {
		return universe().isBoxedType( this );
	}

	@Override
	public HigherPrimitiveDataType unboxedType() {
		return universe().unboxedType( this );
	}

	@Override
	public GroundClass applyTo( List< ? extends World > args ) {
		return applyTo( args,
				typeParameters.stream().map( HigherTypeParameter::getRawType ).collect(
						Collectors.toList() ) );
	}

	@Override
	public GroundClass applyTo(
			List< ? extends World > worldArgs, List< ? extends HigherReferenceType > typeArgs
	) {
		return innerType().applySubstitution( getApplicationSubstitution( worldArgs, typeArgs ) );
	}

	private final Definition innerType = new Definition();

	@Override
	public Definition innerType() {
		return innerType;
	}

	public class Definition extends HigherClassOrInterface.Definition implements GroundClass {

		Definition() {
		}

		@Override
		public HigherClass typeConstructor() {
			return HigherClass.this;
		}

		private final HashMap< Substitution, GroundClass > alphaIndex = new HashMap<>();

		@Override
		public GroundClass applySubstitution( Substitution substitution ) {
			GroundClass result = alphaIndex.get( substitution );
			if( result == null ) {
				result = new Proxy( substitution );
				alphaIndex.put( substitution, result );
			}
			return result;
		}

		protected GroundClass extendedClass = null;

		public void setExtendedClass() {
			HigherClass c = universe().topReferenceType( worldArguments().size() );
			if( c != typeConstructor() ) {
				setExtendedClass( c.applyTo( worldArguments() ) );
			} // else no extended class for Object and Any
		}

		public final void setExtendedClass( GroundClass type ) {
			if( type.typeConstructor().isFinal() ) {
				throw new StaticVerificationException(
						"illegal inheritance, cannot inherit from final '" + type + "'" );
			}
			if( type.typeConstructor() == universe().specialType( Universe.SpecialTypeTag.ENUM )
					&& variety() != Variety.ENUM ) {
				throw new StaticVerificationException(
						"illegal inheritance, only enum types can inherit from '" + universe().specialType(
								Universe.SpecialTypeTag.ENUM ) + "'" );
			}
			if( type.worldArguments().size() != worldArguments().size() ||
					!type.worldArguments().containsAll( worldParameters ) ) {
				throw new StaticVerificationException(
						"illegal inheritance, '" + type + "' and '" + this + "' must have the same roles" );
			}
			extendedClass = type;
		}

		@Override
		public final Optional< ? extends GroundClass > extendedClass() {
			return Optional.ofNullable( extendedClass );
		}

		@Override
		public final Stream< ? extends GroundClassOrInterface > extendedClassesOrInterfaces() {
			if( extendedClass == null ) {
				return super.extendedInterfaces();
			} else {
				return Stream.concat( Stream.of( extendedClass ), super.extendedInterfaces() );
			}
		}

		@Override
		protected boolean isSubtypeOf( GroundDataType type, boolean strict ) {
			return ( !strict && isEquivalentTo( type ) )
					|| ( extendedClass().isPresent() && extendedClass().get().isSubtypeOf( type,
					false ) )
					|| extendedInterfaces().anyMatch( x -> x.isSubtypeOf( type, false ) );
		}

		@Override
		protected boolean isSubtypeOf_relaxed( GroundDataType type, boolean strict ) {
			return ( !strict && isEquivalentTo_relaxed( type ) )
					|| ( extendedClass().isPresent() && extendedClass().get().isSubtypeOf_relaxed( type,
					false ) )
					|| extendedInterfaces().anyMatch( x -> x.isSubtypeOf_relaxed( type, false ) );
		}

		private boolean interfaceFinalised = false;

		@Override
		public final boolean isInterfaceFinalised() {
			return interfaceFinalised;
		}

		@Override
		public void finaliseInterface() {
			assert ( isInheritanceFinalised() && extendedClassesOrInterfaces()
					.allMatch( GroundReferenceType::isInterfaceFinalised ) );
			if( isInterfaceFinalised() ) {
				return;
			}

			// default empty constructor
			if( constructors.isEmpty() ) {
				if( extendedClass != null
						&& extendedClass.constructors().filter( x -> x.isAccessibleFrom( this ) )
						.noneMatch( x -> x.typeParameters().size() == 0 && x.arity() == 0 ) ) {
					throw new StaticVerificationException(
							"there is no default constructor available in '" + extendedClass + "'" );
				} else {
					Member.HigherConstructor c = new Member.HigherConstructor(
							this,
							EnumSet.of( PUBLIC ),
							List.of()
					);
					c.innerCallable().finalise();
					addConstructor( c );
				}
			}

			// (JLS 8.3) Inherit fields from direct superclasses and superinterfaces
			extendedClassesOrInterfaces().flatMap( GroundReferenceType::fields )
					.filter( x -> x.isAccessibleFrom( this )
							&& declaredFields().noneMatch( y -> x.identifier().equals( y.identifier() ) ) )
					.forEach( inheritedFields::add );

			//////// COMPUTE INHERITED METHODS

			// (JLS 8.4.8) A class C inherits from its direct superclass all concrete methods m (both static and
			// instance) of the superclass for which all of the following are true:
			// • m is a member of the direct superclass of C.
			// • m is public, protected, or declared with package access in the same package as C.
			// • No method declared in C has a signature that is a subsignature (§8.4.2) of the signature of m.
			var concreteMethodsInheritedFromSuperclass =
					extendedClass().map( GroundClass::methods ).orElseGet( Stream::empty )
						.filter( m -> m.isConcrete() && m.isAccessibleFrom( this ) )
						.filter( m -> declaredMethods().noneMatch( x -> x.isSubSignatureOf( m ) ) )
						.toList();
            inheritedMethods.addAll( concreteMethodsInheritedFromSuperclass );

			// (JLS 8.4.8) A class C inherits from its direct superclass and direct superinterfaces all abstract and
			// default (§9.4) methods m for which all of the following are true:
			// • m is a member of the direct superclass or a direct superinterface, D, of C.
			// • m is public, protected, or declared with package access in the same package as C.
			// • No method declared in C has a signature that is a subsignature (§8.4.2) of the signature of m.
			// • No concrete method inherited by C from its direct superclass has a signature that is a subsignature
			//   of the signature of m.
			// • There exists no method m' that is a member of the direct superclass or a direct superinterface, D',
			//   of C (m distinct from m', D distinct from D'), such that m' from D' overrides the declaration of
			//   the method m.
			extendedClassesOrInterfaces().flatMap( GroundReferenceType::methods )
					.filter( m -> m.isAbstract() || m.isDefault() )
					.filter( m -> m.isAccessibleFrom( this ) )
					.filter( m -> declaredMethods().noneMatch( x -> x.isSubSignatureOf( m ) ) )
					.filter( m ->
							concreteMethodsInheritedFromSuperclass.stream().noneMatch( x -> x.isSubSignatureOf( m ) )
					)
					.filter( m ->
							// Don't inherit a method if another parent overrides it.
							// For every class or interface D2 in extendedClassesOrInterfaces(), check every method m2
							// in D2 where m != m2 and D != D2. If m2 overrides m from D2, don't inherit m.
							extendedClassesOrInterfaces()
									.filter( D2 -> !D2.isEquivalentTo( m.declarationContext() ) )
									.flatMap( GroundReferenceType::methods )
									.filter( m2 -> m2 != m )
									.noneMatch( m2 -> m2.declarationContext().overrides( m2, m ) )
					)
					.forEach( inheritedMethods::add );

			// TODO We still need to check the requirements on overriding.
			// TODO If the parent method is a selection method, mark the child as a selection method too
			// if( methodToInherit.isSelectionMethod() ) {
			// 		declaredMethod.setSelectionMethod();
			// 	}
			// 	if (methodToInherit.isTypeSelectionMethod()) {
			// 		declaredMethod.setTypeSelectionMethod();
			// 	}


			interfaceFinalised = true;
		}

		/**
		 * Returns the strict superclasses of this class in ascending order (starting from the direct superclass).
		 */
		private Stream< GroundClass > strictSuperclasses() {
			var result = new ArrayList< GroundClass >();
			for ( var D = extendedClass; D != null; D = D.extendedClass().orElse( null ) ) {
				result.add( D );
			}
			return result.stream();
		}

		/**
		 * Returns true iff mC overrides mA from this class. See JLS 8.4.8.1 for details.
		 */
		@Override
		public boolean overrides( Member.HigherMethod mC, Member.HigherMethod mA ) {

			// (JLS 8.4.8.1) An instance method mC declared in or inherited by class C, overrides from C another method
			// mA declared in an *interface* A, iff all of the following are true:
			// 1. A is a superinterface of C.
			// 2. mA is an abstract or default method.
			// 3. The signature of mC is a subsignature (§8.4.2) of the signature of mA.
			//
			// (JLS 8.4.8.1) An instance method mC declared in or inherited by class C, overrides from C another method
			// mA declared in *class* A, iff all of the following are true:
			// 4. A is a superclass of C.
			// 5. C does not inherit mA.
			// 6. The signature of mC is a subsignature (§8.4.2) of the signature of mA.
			// 7. One of the following is true:
			// 	(a) mA is public.
			// 	(b) mA is protected.
			// 	(c) mA is declared with package access in the same package as C, and either C declares mC or mA is a
			//      member of the direct superclass of C.
			//  (d) mA is declared with package access and mC overrides mA from some superclass of C.
			//  (e) mA is declared with package access and mC overrides a method m' from C (m' distinct from mC and mA)
			//      such that m' overrides mA from some superclass of C.

			GroundClassOrInterface C = this;
			GroundClassOrInterface A = mA.declarationContext();

			// The interface must be finalized because we check if mA is inherited by C.
			assert C.isInterfaceFinalised();
			// The JLS presupposes mC is "declared in or inherited by C" and "mA is declared in A".
			if ( C.methods().noneMatch( m -> m == mC ) || A.declaredMethods().noneMatch( m -> m == mA ) ) {
				return false;
			}

			if ( A.isClass() ) {
				if ( !A.isSubtypeOf( C, false ) ) {
					return false;
				}
				if ( C.methods().anyMatch( m -> m == mA ) ) {
					return false;
				}
				if ( !mC.isSubSignatureOf( mA ) ) {
					return false;
				}
				if ( mA.isPublic() || mA.isProtected() ) {
					return true;
				}
				if ( mA.isPackagePrivate() && declarationPackage().equals( A.declarationPackage() ) &&
						( C.declaredMethods().anyMatch( m -> m == mC ) ||
								extendedClass != null && extendedClass.methods().anyMatch( m -> m == mA ) ) ) {
					return true;
				}
				if ( mA.isPackagePrivate() ) {
					// (7.d) Check if mC overrides mA from some strict superclass D of C.
					// Walk the chain; for each D, mC must also be a member of D.
					if ( strictSuperclasses().anyMatch( D -> D.overrides( mC, mA ) ) ) {
						return true;
					}
					// (7.e) Check if there exists m2 in C.methods() \ {mC, mA} such that:
					//  i.  m2 overrides mA from some strict superclass D of C, and
					//  ii. mC overrides m2 from C.
					return C.methods()
							.filter( m2 -> m2 != mC && m2 != mA )
							.filter( m2 -> strictSuperclasses().anyMatch( D -> D.overrides( m2, mA ) ) )
							.anyMatch( m2 -> C.overrides( mC, m2 ) );
				}
				return false;
			}
			else {
				assert A.isInterface();
				return A.isSubtypeOf( C, false ) &&
						( mA.isAbstract() || mA.isDefault() ) &&
						mC.isSubSignatureOf( mA );
			}

		}

		private final List< Member.HigherConstructor > constructors = new LinkedList<>();

		@Override
		public final Stream< ? extends Member.HigherConstructor > constructors() {
			return constructors.stream();
		}

		public void addConstructor( HigherConstructor constructor ) {
			assert ( !isInterfaceFinalised() );
			assert ( constructor.declarationContext() == this );
			for( HigherConstructor x : constructors ) {
				if( x.sameErasureAs( constructor ) ) {
					if( x.sameSignatureAs( constructor ) ) {
						throw new StaticVerificationException( "constructor '" + constructor
								+ "' is already defined in '" + typeConstructor() + "'" );
					} else {
						throw new StaticVerificationException( "constructor '" + constructor
								+ "' clashes with '"
								+ x + "', both constructors have the same erasure" );
					}
				}
			}
			constructors.add( constructor );
		}

		@Override
		public final Stream< ? extends Member.Field > fields() {
			return Stream.concat( declaredFields(), inheritedFields.stream() );
		}

		/**
		 * {@inheritDoc} <p>
		 * (JLS 8.2) The members of a class type are all of the following:
		 *	- Members inherited from its direct superclass (JLS 8.1.4)
		 *	- Members inherited from any direct superinterfaces (JLS 8.1.5)
		 *	- Members declared in the body of the class (JLS 8.1.6)
		 */
		@Override
		public final Stream< ? extends Member.HigherMethod > methods() {
			return Stream.concat( declaredMethods(), inheritedMethods.stream() );
		}

	}

	/** @see HigherDataType.Proxy */
	protected class Proxy extends HigherClassOrInterface.Proxy implements GroundClass {

		Proxy( Substitution substitution ) {
			super( substitution );
		}

		@Override
		public HigherClass typeConstructor() {
			return HigherClass.this;
		}

		@Override
		protected Definition definition() {
			return typeConstructor().innerType();
		}

		@Override
		public GroundClass applySubstitution( Substitution substitution ) {
			return definition().applySubstitution( substitution().andThen( substitution ) );
		}

		public final Optional< ? extends GroundClass > extendedClass() {
			return definition().extendedClass().map( x -> x.applySubstitution( substitution() ) );
		}

		@Override
		protected boolean isSubtypeOf( GroundDataType type, boolean strict ) {
			return ( !strict && isEquivalentTo( type ) )
					|| ( extendedClass().isPresent() && extendedClass().get().isSubtypeOf( type,
					false ) )
					|| extendedInterfaces().anyMatch( x -> x.isSubtypeOf( type, false ) );
		}

		@Override
		protected boolean isSubtypeOf_relaxed( GroundDataType type, boolean strict ) {
			return ( !strict && isEquivalentTo_relaxed( type ) )
					|| ( extendedClass().isPresent() && extendedClass().get().isSubtypeOf_relaxed( type,
					false ) )
					|| extendedInterfaces().anyMatch( x -> x.isSubtypeOf_relaxed( type, false ) );
		}

		@Override
		public final Stream< ? extends Member.HigherConstructor > constructors() {
			return definition().constructors().map( x -> x.applySubstitution( substitution() ) );
		}

	}

}
