package com.tfsh.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.HashSet;
import java.util.Set;

/**
 * 单测辅助：手动初始化 MyBatis-Plus 的实体元信息。
 *
 * 为什么需要：`Wrappers.lambdaQuery()` / `lambdaUpdate()` 会把方法引用解析成列名，
 * 依赖 `TableInfoHelper` 里的缓存；纯单测（不起 Spring 容器）里这份缓存是空的，
 * 直接调用会抛 "can not find lambda cache for this entity"。
 */
public final class MybatisPlusTestSupport {

    private static final Set<Class<?>> INITIALIZED = new HashSet<>();

    private MybatisPlusTestSupport() {
    }

    public static synchronized void init(Class<?>... entities) {
        for (Class<?> entity : entities) {
            if (!INITIALIZED.add(entity)) {
                continue;
            }
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace(entity.getName() + "Mapper");
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
