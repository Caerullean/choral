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

		/**
		 * Returns the strict superclasses of this class in ascending order (starting from the direct superclass).
		 */
		private Stream< GroundClass > strictSuperclasses() {
			if ( extendedClass == null ) return Stream.empty();
			return Stream.iterate( extendedClass, Objects::nonNull, d -> d.extendedClass().orElse( null ) );
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

			// Precompute a grouping of ancestor methods by name for efficient lookup.
			Map< String, List< Member.HigherMethod > > ancestorsByName =
					allAncestorMethods.stream()
							.collect( Collectors.groupingBy( Member.HigherMethod::identifier ) );


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
			allAncestorMethods.stream()
					.filter( m -> m.isAbstract() || m.isDefault() )
					.filter( m -> m.isAccessibleFrom( this ) )
					.filter( m -> declaredMethods().noneMatch( x -> x.isSubSignatureOf( m ) ) )
					.filter( m ->
							concreteMethodsInheritedFromSuperclass.stream().noneMatch( x -> x.isSubSignatureOf( m ) )
					)
					.filter( m -> !overriddenByAnother.contains( m ) )
					.forEach( inheritedMethods::add );


			//// CHECK OVERRIDE REQUIREMENTS AND ERASURE CLASHES

			// (JLS 8.4.8) For each declared method mC, check override requirements against any
			// ancestor method it overrides, and detect erasure clashes. These two checks iterate
			// the same (declared × ancestor) pairs, so they are fused into a single pass here.
			methods().forEach( mC -> {
				for ( Member.HigherMethod mA : ancestorsByName.getOrDefault( mC.identifier(), List.of() ) ) {
					if ( this.overrides( mC, mA ) ) {
						checkOverrideRequirementsOrThrow( mC, mA );
					}
					// (JLS 8.4.8.3) It is a compile-time error if a type declaration C has a member
					// method mC and there exists a method mA declared in C or a supertype of C such
					// that all of the following are true:
					// • mA and mC have the same name.
					// • mA is accessible from C.
					// • The signature of mC is not a subsignature (§8.4.2) of the signature of mA.
					// • The signature of mC or some method mC overrides (directly or indirectly)
					//   has the same erasure as the signature of mA or some method mA overrides
					//   (directly or indirectly).
					if ( mA.isAccessibleFrom( this )
							&& !mC.isSubSignatureOf( mA )
							&& mC.sameErasureAs( mA ) ) {
						throw new StaticVerificationException(
								"method '" + mC + "' in '" + this + "' clashes with method '"
										+ mA + "' in '" + mA.declarationContext()
										+ "', both methods have the same erasure" );
					}
				}
			} );

			// (JLS 8.4.8.4) It is possible for a class to inherit multiple methods with
			// override-equivalent signatures.
			//
			// Rule A: It is a compile-time error if a class C inherits a concrete method whose
			// signature is override-equivalent with another method inherited by C.
			//
			// Rule B: It is a compile-time error if a class C inherits a default method whose
			// signature is override-equivalent with another method inherited by C, UNLESS there
			// exists an abstract method declared in a superclass (not just a superinterface) of C
			// and inherited by C that is override-equivalent with the two methods. In that case,
			// C is necessarily abstract and is considered to inherit all the methods.

			// Precompute the set of inherited methods that are abstract and declared in a class.
			var abstractFromClass = inheritedMethods.stream()
					.filter( m -> m.isAbstract() && m.declarationContext().isClass() )
					.toList();

			for( int i = 0; i < inheritedMethods.size(); i++ ) {
				Member.HigherMethod m1 = inheritedMethods.get( i );
				for( int j = i + 1; j < inheritedMethods.size(); j++ ) {
					Member.HigherMethod m2 = inheritedMethods.get( j );
					if( !m1.isOverrideEquivalentTo( m2 ) ) continue;

					// Rule A: concrete conflict
					if( m1.isConcrete() || m2.isConcrete() ) {
						throw new StaticVerificationException(
								"class '" + this + "' inherits two override-equivalent methods '"
										+ m1 + "' from '" + m1.declarationContext()
										+ "' and '" + m2 + "' from '" + m2.declarationContext() + "'" );
					}

					// Rule B: default-default conflict (abstract-default pairs are fine)
					if( m1.isDefault() && m2.isDefault() ) {
						boolean hasAbstractFromSuperclass = abstractFromClass.stream()
								.filter( m3 -> m3 != m1 && m3 != m2 )
								.anyMatch( m3 -> m3.isOverrideEquivalentTo( m1 ) );
						if( !hasAbstractFromSuperclass ) {
							throw new StaticVerificationException(
									"class '" + this + "' inherits two override-equivalent default methods '"
											+ m1 + "' from '" + m1.declarationContext()
											+ "' and '" + m2 + "' from '" + m2.declarationContext() + "'" );
						}
					}
				}
			}

			interfaceFinalised = true;
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

			if( A.isInterface() ) {
				return C.isSubtypeOf( A, false ) &&
						( mA.isAbstract() || mA.isDefault() ) &&
						mC.isSubSignatureOf( mA );
			}
			else {
				assert A.isClass();
				if ( !C.isSubtypeOf( A, false ) ) {
					return false;
				}
				if ( inheritedMethods.stream().anyMatch( m -> m.equals( mA ) ) ) {
					// TODO Implement equality for proxy callables
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

		}

		private void checkOverrideRequirementsOrThrow(Member.HigherMethod child, Member.HigherMethod parent) {
			// (8.4.3.3) Ensure we're not overriding a final method
			if( parent.isFinal() ) {
				throw new StaticVerificationException( "method '" + child
						+ "' in '" + this + "' cannot override final method '"
						+ parent + "' in '" + parent.declarationContext() + "'" );
			}
			// (8.4.8.1) Ensure instance methods don't override static methods
			if( !child.isStatic() && parent.isStatic() ) {
				throw new StaticVerificationException( "instance method '" + child
						+ "' in '" + this + "' cannot override static method '"
						+ parent + "' in '" + parent.declarationContext() + "'" );
			}
			// (8.4.8.2) Ensure static methods don't hide instance methods
			if( child.isStatic() && !parent.isStatic() ) {
				throw new StaticVerificationException( "static method '" + child
						+ "' in '" + this + "' cannot override instance method '"
						+ parent + "' in '" + parent.declarationContext() + "'" );
			}
			// (8.4.8.3) Ensure method return types are covariant
			if( !child.isReturnTypeSubstitutableFor(parent) ) {
				throw new StaticVerificationException( "method '" + child
						+ "' in '" + this + "' clashes with method '"
						+ parent + "' in '" + parent.declarationContext()
						+ "', attempting to use incompatible return type" );
			}

			// (8.4.8.3) JLS says we should issue a warning if child is not a subtype of parent; we skip that check.
			// (8.4.8.3) Choral doesn't have checked exceptions yet, so we skip those checks.

			// (8.4.8.3) Ensure the access modifiers are compatible
			if( child.isPrivate() || ( parent.isPublic() && !child.isPublic() )
					|| ( parent.isProtected() && child.isPackagePrivate() ) ) {
				throw new StaticVerificationException( "method '" + child
						+ "' in '" + this + "' clashes with method '"
						+ parent + "' in '" + parent.declarationContext()
						+ "', attempting to assign weaker access privileges '"
						+ ModifierUtils.prettyAccess( child.modifiers() ) + "' to '"
						+ ModifierUtils.prettyAccess( parent.modifiers() ) + "'" );
			}

			// If the parent method is a selection method, mark the child as a selection method too
			if( parent.isSelectionMethod() ) {
				child.setSelectionMethod();
			}
			if( parent.isTypeSelectionMethod() ) {
				child.setTypeSelectionMethod();
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

		@Override
		public boolean overrides( Member.HigherMethod m1, Member.HigherMethod m2 ) {
			return definition().overrides(
					m1,
					m2
					// TODO Apply the reverse substitution to the methods
			);
		}

	}

}
