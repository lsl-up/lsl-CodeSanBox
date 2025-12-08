public class NormalCompute {

    public static void main(String[] args) {
        System.out.println("开始正常计算测试");
        
        if (args.length >= 2) {
            try {
                int a = Integer.parseInt(args[0]);
                int b = Integer.parseInt(args[1]);
                
                int sum = a + b;
                int diff = a - b;
                int product = a * b;
                double quotient = (double) a / b;
                
                System.out.println("输入参数: a=" + a + ", b=" + b);
                System.out.println("加法结果: " + sum);
                System.out.println("减法结果: " + diff);
                System.out.println("乘法结果: " + product);
                System.out.println("除法结果: " + quotient);
                
            } catch (NumberFormatException e) {
                System.out.println("参数格式错误: " + e.getMessage());
            } catch (ArithmeticException e) {
                System.out.println("计算错误: " + e.getMessage());
            }
        } else {
            System.out.println("请提供两个整数参数");
            System.out.println("示例: java NormalCompute 10 5");
        }
    }
} 