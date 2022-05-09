package com.aimuge.mugegateway.apollo;

import com.ctrip.framework.apollo.enums.PropertyChangeType;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 基于apollo实现路由动态刷新
 *
 * @Author huyanlong
 * @Date 2022/5/9 22:18
 */
@Component
@Slf4j
public class GatewayRoutesRefresh implements ApplicationContextAware, ApplicationEventPublisherAware {
    private static final String ID_PATTERN = "spring\\.cloud\\.gateway\\.routes\\[\\d+\\]\\.id";
    private static final String DEFAULT_FILTER_PATTERN = "spring\\.cloud\\.gateway\\.default-filters\\[\\d+\\]\\.name";
    private ApplicationContext applicationContext;

    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private GatewayProperties gatewayProperties;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @ApolloConfigChangeListener(value = "application.yaml", interestedKeyPrefixes = "spring.cloud.gateway.")
    public void onChange(ConfigChangeEvent changeEvent) {
        refreshGatewayRoutes(changeEvent);
    }

    /**
     * 刷新org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator中定义的routes
     *
     * @param changeEvent
     */
    private void refreshGatewayRoutes(ConfigChangeEvent changeEvent) {
        log.info("Refreshing GatewayRoutes!");
        preDestroyGatewayRoutes(changeEvent);
        this.applicationContext.publishEvent(new EnvironmentChangeEvent(changeEvent.changedKeys()));
        refreshGatewayRouteDefinition();
        log.info("GatewayRoutes refreshed!");
    }

    /**
     * 清空两个集合清空
     *
     * @param changeEvent
     */
    private synchronized void preDestroyGatewayRoutes(ConfigChangeEvent changeEvent) {
        log.info("Pre Destroy GatewayRoutes!");
        final boolean needClearRoutes = this.checkNeedClear(changeEvent, ID_PATTERN, this.gatewayProperties.getRoutes().size());
        if (needClearRoutes) {
            this.gatewayProperties.setRoutes(new ArrayList<>());
        }
        final boolean needClearDefaultFilters = this.checkNeedClear(changeEvent, DEFAULT_FILTER_PATTERN, this.gatewayProperties.getDefaultFilters().size());
        if (needClearDefaultFilters) {
            this.gatewayProperties.setDefaultFilters(new ArrayList<>());
        }
        log.info("Pre Destroy GatewayRoutes finished!");
    }

    private void refreshGatewayRouteDefinition() {
        log.info("Refreshing Gateway RouteDefinition!");
        this.applicationEventPublisher.publishEvent(new RefreshRoutesEvent(this));
        log.info("Gateway RouteDefinition refreshed!");
    }

    /**
     * 根据changeEvent和定义的pattern匹配key，如果所有对应PropertyChangeType为DELETED则需要清空GatewayProperties里相关集合
     *
     * @param changeEvent
     * @param pattern
     * @param existSize
     * @return
     */
    private boolean checkNeedClear(ConfigChangeEvent changeEvent, String pattern, int existSize) {
        return changeEvent.changedKeys().stream().filter(key -> key.matches(pattern))
                .filter(key -> {
                    ConfigChange change = changeEvent.getChange(key);
                    return PropertyChangeType.DELETED.equals(change.getChangeType());
                }).count() == existSize;
    }
}
