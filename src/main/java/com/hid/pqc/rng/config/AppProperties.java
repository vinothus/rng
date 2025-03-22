package com.hid.pqc.rng.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Scheduler scheduler = new Scheduler();
    private Random random = new Random();

    public Scheduler getScheduler() {
        return scheduler;
    }

    public Random getRandom() {
        return random;
    }

    public static class Scheduler {
        private long fixedRate;
        private String tableName;

        public long getFixedRate() {
            return fixedRate;
        }

        public void setFixedRate(long fixedRate) {
            this.fixedRate = fixedRate;
        }
        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }

    public static class Random {
        private int count;
        private int length;
        private String tableName;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }
        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
    }
}