package ClassLifter.InheritedMethods;

import choral.annotations.Choreography;

@Choreography( role = "A", name = "InheritedMethods" )
class InheritedMethods {
	public void sayHello() {
		MiddleChild test = new MiddleChild();
		System.out.println( test.toString() );
	}

}
