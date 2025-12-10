package com.lsl.lslojcodesandbox.Utils;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 进程执行工具类
 * 负责解析 Linux /usr/bin/time -v 的输出结果
 */
public class ProcessUtils {

    /**
     * 从 time -v 的输出中提取最大驻留内存 (KB -> Byte)
     * 格式示例：Maximum resident set size (kbytes): 35840
     */
    public static Long extractMemory(String stderr) {
        if (!StringUtils.hasText(stderr)) {
            return 0L;
        }
        // 正则匹配 kbytes
        Pattern pattern = Pattern.compile("Maximum resident set size \\(kbytes\\): (\\d+)");
        Matcher matcher = pattern.matcher(stderr);
        if (matcher.find()) {
            long kbytes = Long.parseLong(matcher.group(1));
            return kbytes * 1024; // 转为字节
        }
        return 0L;
    }

    /**
     * 从 time -v 的输出中提取耗时 (ms)
     * 格式示例：Elapsed (wall clock) time (h:mm:ss or m:ss): 0:00.05
     */
    public static Long extractTime(String stderr) {
        if (!StringUtils.hasText(stderr)) {
            return 0L;
        }
        Pattern pattern = Pattern.compile("Elapsed \\(wall clock\\) time \\(h:mm:ss or m:ss\\): (\\S+)");
        Matcher matcher = pattern.matcher(stderr);
        if (matcher.find()) {
            String timeStr = matcher.group(1);
            // 解析时间字符串 (可能格式 1:23.45 或 0:00.05)
            String[] parts = timeStr.split(":");
            double seconds = 0;
            if (parts.length == 2) { // m:ss.xx
                seconds = Double.parseDouble(parts[0]) * 60 + Double.parseDouble(parts[1]);
            } else if (parts.length == 3) { // h:mm:ss.xx
                seconds = Double.parseDouble(parts[0]) * 3600 + Double.parseDouble(parts[1]) * 60 + Double.parseDouble(parts[2]);
            }
            return (long) (seconds * 1000); // 转为毫秒
        }
        return 0L;
    }
}