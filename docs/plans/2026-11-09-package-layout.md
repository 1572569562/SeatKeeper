# Spring Boot包结构修正计划

依据：用户明确要求按beemines/java-springboot-backend-standards建立软件包结构，而不是以Maven模块划分作为模板。采用api/config/exception/mapper/model(dto,po,vo,result)/service/impl/utils。

- [x] 独立模板改为单模块可运行SpringBoot应用，basePackage=cn.edu.stu.template。包含薄Controller、Service接口及实现、统一Result/ResultUtil、错误码/业务异常/全局异常、MyBatis配置和中文包说明。
- [x] SeatKeeper保留团队common/service模块边界，应用basePackage改为cn.edu.stu.seatkeeper；接口、实现、Mapper、PO、异常和工具归位，Mapper XML全限定名同步。
- [x] 现有数据库结构/迁移文件原样保留，已有事务隔离、锁、幂等和通知顺序保持；健康接口仍code=OK/message/data，避免改变团队契约。普通MyBatis保留，不因规范示例使用MyBatisPlus而强行替换。
- [x] 用健康MVC测试和原有真实MySQL14项/测试库保护5项回归，补包结构职责检查与DTO边界用例，验证模板启动。
- [x] README、数据字典和交接文档同步新包名/类型，旧源码在工作区tmp下备份后移除，避免重名Bean或陈旧架构。
- [x] 独立审查修正并最终验证UTF8/中文注释/Javadoc、构建与文档链接。

参数与实现选择：PO保留Java17不可变record以避免改变映射行为；接口与前端模型分离，当前健康返回HealthVO。模板尚无业务表，mapper与model.po用中文package-info说明扩展位置，不创建假业务表。完整工程DTO用于服务调用参数，公开数据服务由接口/实现组成。公共响应类型采用Result命名，但线上的code/message字段保持团队原契约。


执行记录：包职责测试先失败于不存在的api.HealthController，调整后通过。独立审查已核对原快照中的七个模型、四个Mapper、四个Service和两份迁移；已修复HTTP协议异常及参数转换被兜底为500的问题，补测先失败后通过。最终模板12/12、正式工程33/33；两份最终JAR实启成功，UTF-8、中文Java注释、包路径及迁移原文检查通过。旧模板模块源码已在工作区tmp/package-layout-before.zip备份后移除。

实施取舍：继续使用团队约定的MyBatis XML、Java17 record与code/message/data响应，不切换参考示例中的MyBatis-Plus和数值状态码；如果团队以后更改接口格式，需要共同升级调用端。保留common/service模块满足现有团队边界，独立复用模板采用单模块。其他成员的认证、客户端和四机/规模验收沿用原分工。
