public class RecursionTest {

    public static void main(String[] args) {
        System.out.println("开始递归深度测试");
        
        if (args.length >= 1) {
            try {
                int depth = Integer.parseInt(args[0]);
                System.out.println("递归深度: " + depth);
                
                long result = factorial(depth);
                System.out.println("阶乘结果: " + result);
                
            } catch (NumberFormatException e) {
                System.out.println("参数格式错误: " + e.getMessage());
            } catch (StackOverflowError e) {
                System.out.println("栈溢出异常: " + e.getMessage());
            }
        } else {
            System.out.println("请提供递归深度参数");
            System.out.println("示例: java RecursionTest 1000");
        }
    }
    
    public static long factorial(int n) {
        if (n <= 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }
} 