public class IsolationTest {

    public static void main(String[] args) {
        System.out.println("=== Docker容器隔离测试 ===");
        
        // 1. 进程信息测试
        System.out.println("\n1. 进程信息测试:");
        System.out.println("当前进程ID: " + ProcessHandle.current().pid());
        System.out.println("父进程ID: " + ProcessHandle.current().parent().orElse(null));
        
        // 2. 系统信息测试
        System.out.println("\n2. 系统信息测试:");
        System.out.println("操作系统: " + System.getProperty("os.name"));
        System.out.println("Java版本: " + System.getProperty("java.version"));
        System.out.println("用户目录: " + System.getProperty("user.dir"));
        System.out.println("临时目录: " + System.getProperty("java.io.tmpdir"));
        
        // 3. 内存信息测试
        System.out.println("\n3. 内存信息测试:");
        Runtime runtime = Runtime.getRuntime();
        System.out.println("最大内存: " + (runtime.maxMemory() / 1024 / 1024) + "MB");
        System.out.println("已分配内存: " + (runtime.totalMemory() / 1024 / 1024) + "MB");
        System.out.println("空闲内存: " + (runtime.freeMemory() / 1024 / 1024) + "MB");
        
        // 4. 文件系统测试
        System.out.println("\n4. 文件系统测试:");
        try {
            java.io.File[] roots = java.io.File.listRoots();
            for (java.io.File root : roots) {
                System.out.println("根目录: " + root.getPath() + 
                                 " 可用空间: " + (root.getFreeSpace() / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            System.out.println("文件系统访问失败: " + e.getMessage());
        }
        
        // 5. 网络测试
        System.out.println("\n5. 网络测试:");
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            System.out.println("本机地址: " + localHost.getHostAddress());
            System.out.println("本机名称: " + localHost.getHostName());
        } catch (Exception e) {
            System.out.println("网络访问失败: " + e.getMessage());
        }
        
        // 6. 环境变量测试
        System.out.println("\n6. 环境变量测试:");
        System.out.println("PATH: " + System.getenv("PATH"));
        System.out.println("HOME: " + System.getenv("HOME"));
        System.out.println("USER: " + System.getenv("USER"));
        
        System.out.println("\n=== 隔离测试完成 ===");
    }
} 