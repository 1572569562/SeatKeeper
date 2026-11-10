package cn.edu.stu.seatkeeper.config;

import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** 集中配置下划线映射与不可变模型的构造参数映射。 */
@Configuration
public class MybatisConfig {
    /** @return 与项目持久化模型对应的映射规则 */
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            // 数据库字段使用下划线，Java模型使用驼峰命名；二者通过此规则对应。
            configuration.setMapUnderscoreToCamelCase(true);
            // 不可变记录通过构造参数名映射，避免依赖查询列顺序。
            configuration.setArgNameBasedConstructorAutoMapping(true);
        };
    }
}
