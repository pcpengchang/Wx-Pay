package com.ace.payment.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.support.http.StatViewServlet;
import com.alibaba.druid.support.http.WebStatFilter;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletRegistration;
import javax.sql.DataSource;
import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;

@Configuration
@MapperScan("com.ace.payment.mapper")
@EnableTransactionManagement
public class MyBatisPlusConfig {

//    @ConfigurationProperties("spring.datasource")
//    @Bean
//    public DataSource dataSource() throws SQLException {
//        DruidDataSource druidDataSource = new DruidDataSource();
//        druidDataSource.setFilters("stat, wall");
//
//        return druidDataSource;
//    }

    //相当于是配置xml
    //  <servlet>
    //      <servlet-name>DruidStatView</servlet-name>
    //      <servlet-class>com.alibaba.druid.support.http.StatViewServlet</servlet-class>
    //  </servlet>
    //  <servlet-mapping>
    //      <servlet-name>DruidStatView</servlet-name>
    //      <url-pattern>/druid/*</url-pattern>
    //  </servlet-mapping>
//    @Bean//开启监控页
//    public ServletRegistrationBean statViewServlet(){
//        StatViewServlet statViewServlet = new StatViewServlet();
//        ServletRegistrationBean <StatViewServlet>servletRegistration = new ServletRegistrationBean<>(statViewServlet, "/druid/*");
//
//        //内置监控页面的首页是/druid/index.html
//        return servletRegistration;
//    }
//
//    //Web关联监控配置
//    @Bean
//    public FilterRegistrationBean webStatFilter(){
//        WebStatFilter webStatFilter = new WebStatFilter();
//        FilterRegistrationBean<WebStatFilter>  filterRegistrationBean = new FilterRegistrationBean<>(webStatFilter);
//        filterRegistrationBean.setUrlPatterns(Arrays.asList("/*"));
//        filterRegistrationBean.addInitParameter("exclusions", "*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*");
//        return filterRegistrationBean;
//    }
}
