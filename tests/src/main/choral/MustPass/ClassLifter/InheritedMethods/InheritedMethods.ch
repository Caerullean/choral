package ClassLifter.InheritedMethods;

class InheritedMethods@(A){
    public void sayHello(){
        MiddleChild@A test = new MiddleChild@A();
        System@A.out.println(test.toString());
    }
}