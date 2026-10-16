package com.example.betterluck.integration;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS 集成的入口。
 *
 * <p>KubeJS 的插件不是靠注解发现的，而是读各模组 jar 根目录下的
 * {@code kubejs.plugins.txt}（这个文件在本工程里是
 * {@code src/main/resources/kubejs.plugins.txt}），每行一个类名，
 * 行尾可以再写一个**依赖的模组 id**，没装就跳过。我们两边都要生效，所以只写类名。
 *
 * <p>KubeJS 没装时这个类不会被加载，所以 {@code compileOnly} 依赖不会在运行时报错。
 */
public class BetterLuckKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry event) {
        event.add("BetterLuck", new BetterLuckBindings());
    }

    @Override
    public void registerClasses(ClassFilter event) {
        // 放行绑定类本身，免得脚本调它上面的方法被类过滤器拦下
        event.allow(BetterLuckBindings.class);
    }
}
