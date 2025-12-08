public class MultiThreadTest {

    public static void main(String[] args) {
        System.out.println("开始多线程测试");
        
        if (args.length >= 1) {
            try {
                int threadCount = Integer.parseInt(args[0]);
                System.out.println("线程数量: " + threadCount);
                
                Thread[] threads = new Thread[threadCount];
                
                for (int i = 0; i < threadCount; i++) {
                    final int threadId = i;
                    threads[i] = new Thread(() -> {
                        System.out.println("线程 " + threadId + " 开始执行");
                        
                        // 模拟一些计算工作
                        long sum = 0;
                        for (int j = 0; j < 1000000; j++) {
                            sum += j;
                        }
                        
                        System.out.println("线程 " + threadId + " 计算完成，结果: " + sum);
                    });
                    
                    threads[i].start();
                }
                
                // 等待所有线程完成
                for (Thread thread : threads) {
                    thread.join();
                }
                
                System.out.println("所有线程执行完成");
                
            } catch (NumberFormatException e) {
                System.out.println("参数格式错误: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("线程等待中断: " + e.getMessage());
            }
        } else {
            System.out.println("请提供线程数量参数");
            System.out.println("示例: java MultiThreadTest 5");
        }
    }
} 