public class InfiniteLoop {

    public static void main(String[] args) {
        System.out.println("开始无限循环测试");
        int i = 0;
        while (true) {
            i++;
            if (i % 1000000 == 0) {
                System.out.println("循环次数: " + i);
            }
        }
    }
} 