package org.example.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.ResponseResult;
import org.example.model.Attendant;
import org.example.model.AttendantQualification;
import org.example.model.Order;
import org.example.model.User;
import org.example.model.request.AttendantCancelOrderRequest;
import org.example.model.request.AttendantQualificationUpdateRequest;
import org.example.model.request.AttendantProfileUpdateRequest;
import org.example.model.request.OrderListQueryRequest;
import org.example.model.response.AttendantAvatarUploadResponse;
import org.example.model.response.AttendantProfileResponse;
import org.example.model.response.AttendantPublicReviewResponse;
import org.example.model.response.OrderAcceptResponse;
import org.example.model.response.OrderListResponse;
import org.example.model.response.PagedResponse;
import org.example.model.OrderEvaluation;
import org.example.service.AttendantService;
import org.example.service.FileStorageService;
import org.example.service.OrderEvaluationService;
import org.example.service.OrderService;
import org.example.service.UserService;
import org.example.util.AuthUtil;
import org.example.unity.OrderSettlementCalculator;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 陪诊师端接口控制器
 */
@RestController
@RequestMapping("/attendant")
@Api(tags = "陪诊师端接口", description = "陪诊师资料维护、资质提交、接单、服务进度更新、评价回复等接口")
@RequiredArgsConstructor
@Slf4j
public class AttendantController {

    private final OrderService orderService;
    private final AttendantService attendantService;
    private final UserService userService;
    private final OrderEvaluationService evaluationService;
    private final FileStorageService fileStorageService;

    /**
     * 获取陪诊师个人资料
     */
    @GetMapping("/profile/{userId}")
    @ApiOperation(value = "获取陪诊师个人资料", notes = "根据陪诊师用户ID查询个人中心资料、资质状态、统计信息与钱包摘要。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 404, message = "用户不存在"),
            @ApiResponse(code = 500, message = "获取资料失败")
    })
    public ResponseResult<AttendantProfileResponse> getProfile(
            @ApiParam(value = "陪诊师用户ID", required = true, example = "21")
            @PathVariable Integer userId) {
        try {
            AttendantProfileResponse profile = attendantService.getProfile(userId);
            if (profile == null) {
                return ResponseResult.error("用户不存在");
            }

            return ResponseResult.success(profile);
        } catch (Exception e) {
            log.error("获取陪诊师资料失败", e);
            return ResponseResult.error("获取资料失败");
        }
    }

    @GetMapping("/profile/{userId}/reviews")
    @ApiOperation(value = "获取陪诊师公开评价摘要", notes = "用户端详情页读取指定陪诊师最近的公开评价摘要，仅返回脱敏后的昵称与评价内容。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 500, message = "获取评价摘要失败")
    })
    public ResponseResult<List<AttendantPublicReviewResponse>> getPublicReviews(
            @ApiParam(value = "陪诊师用户ID", required = true, example = "21")
            @PathVariable Integer userId,
            @ApiParam(value = "返回条数上限", example = "6")
            @RequestParam(defaultValue = "6") Integer limit) {
        try {
            int safeLimit = Math.max(1, Math.min(limit == null ? 6 : limit, 12));
            List<AttendantPublicReviewResponse> reviews = orderService.findAllOrders().stream()
                    .filter(order -> order.getAttendantId() != null && order.getAttendantId().equals(userId))
                    .filter(order -> Integer.valueOf(6).equals(order.getOrderStatus()))
                    .map(this::buildPublicReviewResponse)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(
                            AttendantPublicReviewResponse::getCreateTime,
                            Comparator.nullsLast(Date::compareTo)
                    ).reversed())
                    .limit(safeLimit)
                    .collect(Collectors.toList());
            return ResponseResult.success(reviews);
        } catch (Exception e) {
            log.error("获取陪诊师公开评价摘要失败，userId={}", userId, e);
            return ResponseResult.error("获取评价摘要失败");
        }
    }

    /**
     * 更新陪诊师个人资料
     */
    @PutMapping("/profile/{userId}")
    @ApiOperation(value = "更新陪诊师个人资料", notes = "更新陪诊师基础信息与扩展资料。请求体字段按需传入，未传字段不会被强制覆盖。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "更新成功"),
            @ApiResponse(code = 400, message = "请求参数不能为空"),
            @ApiResponse(code = 404, message = "用户不存在"),
            @ApiResponse(code = 500, message = "更新失败")
    })
    public ResponseResult<String> updateProfile(
            @ApiParam(value = "陪诊师用户ID", required = true, example = "21")
            @PathVariable Integer userId,
            @ApiParam(value = "资料更新请求体", required = true)
            @RequestBody AttendantProfileUpdateRequest request) {
        try {
            if (request == null) {
                return ResponseResult.error("请求参数不能为空");
            }
            User existUser = userService.findById(userId);
            if (existUser == null) {
                return ResponseResult.error("用户不存在");
            }

            boolean hasUserUpdates = request.getName() != null
                    || request.getPhone() != null
                    || request.getAvatarUrl() != null;
            if (hasUserUpdates) {
                User user = new User();
                user.setId(userId);
                user.setName(request.getName());
                user.setPhone(request.getPhone());
                user.setAvatar(request.getAvatarUrl());
                userService.update(user);
            }

            boolean hasAttendantUpdates = request.getIntroduction() != null
                    || request.getProfessionalField() != null
                    || request.getExperienceYears() != null
                    || request.getHospitalName() != null
                    || request.getCertificate() != null;
            if (hasAttendantUpdates) {
                Attendant attendant = new Attendant();
                attendant.setUserId(userId);
                attendant.setIntroduction(request.getIntroduction());
                attendant.setProfessionalField(request.getProfessionalField());
                attendant.setExperienceYears(request.getExperienceYears());
                attendant.setHospitalName(request.getHospitalName());
                attendant.setCertificate(request.getCertificate());
                attendantService.update(attendant);
            }

            return ResponseResult.success("更新成功");
        } catch (IllegalArgumentException e) {
            return ResponseResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新陪诊师资料失败，userId={}", userId, e);
            return ResponseResult.error("更新失败");
        }
    }

    /**
     * 更新陪诊师三证资质信息
     */
    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiOperation(value = "上传陪诊师头像", notes = "上传头像后立即更新当前陪诊师账号头像字段，并返回最新头像地址。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "上传成功"),
            @ApiResponse(code = 400, message = "图片格式或大小不合法"),
            @ApiResponse(code = 401, message = "未登录"),
            @ApiResponse(code = 500, message = "上传失败")
    })
    public ResponseResult<AttendantAvatarUploadResponse> uploadAvatar(
            @ApiParam(value = "头像文件", required = true)
            @RequestParam("file") MultipartFile file,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            User existUser = userService.findById(currentUserId);
            if (existUser == null) {
                return ResponseResult.error("用户不存在");
            }

            String avatarUrl = fileStorageService.storeAvatar(file);
            User user = new User();
            user.setId(currentUserId);
            user.setAvatar(avatarUrl);
            userService.update(user);

            AttendantAvatarUploadResponse response = new AttendantAvatarUploadResponse();
            response.setUserId(currentUserId);
            response.setAvatarUrl(avatarUrl);
            return ResponseResult.success(response);
        } catch (IllegalArgumentException e) {
            return ResponseResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传陪诊师头像失败", e);
            return ResponseResult.error("上传头像失败");
        }
    }

    @PutMapping("/qualification/{userId}")
    @ApiOperation(value = "更新陪诊师三证资质信息", notes = "更新身份证、执业证书、健康证上传状态及文件地址，不会自动触发审核。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "资质信息更新成功"),
            @ApiResponse(code = 400, message = "请求参数不能为空"),
            @ApiResponse(code = 404, message = "用户不存在"),
            @ApiResponse(code = 500, message = "资质信息更新失败")
    })
    public ResponseResult<String> updateQualification(
            @ApiParam(value = "陪诊师用户ID", required = true, example = "21")
            @PathVariable Integer userId,
            @ApiParam(value = "资质更新请求体", required = true)
            @RequestBody AttendantQualificationUpdateRequest request,
            @ApiIgnore HttpServletRequest httpRequest) {
        try {
            if (request == null) {
                return ResponseResult.error("请求参数不能为空");
            }
            Integer currentUserId = AuthUtil.getCurrentUserId(httpRequest);
            if (currentUserId == null || !currentUserId.equals(userId)) {
                return new ResponseResult<>(403, "无权修改他人资质", null);
            }
            User existUser = userService.findById(userId);
            if (existUser == null) {
                return ResponseResult.error("用户不存在");
            }

            AttendantQualification qualification = new AttendantQualification();
            qualification.setUserId(userId);
            qualification.setIdCardUploaded(request.getIdCardUploaded());
            qualification.setPracticeCertUploaded(request.getPracticeCertUploaded());
            qualification.setHealthCertUploaded(request.getHealthCertUploaded());
            qualification.setIdCardFileUrl(request.getIdCardFileUrl());
            qualification.setIdCardFrontFileUrl(request.getIdCardFrontFileUrl());
            qualification.setIdCardFrontScanFileUrl(request.getIdCardFrontScanFileUrl());
            qualification.setIdCardBackFileUrl(request.getIdCardBackFileUrl());
            qualification.setIdCardBackScanFileUrl(request.getIdCardBackScanFileUrl());
            qualification.setPracticeCertFileUrl(request.getPracticeCertFileUrl());
            qualification.setPracticeCertScanFileUrl(request.getPracticeCertScanFileUrl());
            qualification.setHealthCertFileUrl(request.getHealthCertFileUrl());
            qualification.setHealthCertScanFileUrl(request.getHealthCertScanFileUrl());
            qualification.setPracticeCertExpireDate(request.getPracticeCertExpireDate());
            qualification.setHealthCertExpireDate(request.getHealthCertExpireDate());

            attendantService.updateQualification(userId, qualification);
            return ResponseResult.success("资质信息更新成功");
        } catch (Exception e) {
            log.error("更新陪诊师资质失败，userId={}", userId, e);
            return ResponseResult.error("资质信息更新失败");
        }
    }

    /**
     * 提交资质审核
     */
    @PostMapping("/qualification/{userId}/submit")
    @ApiOperation(value = "提交资质审核", notes = "在三证资料更新完成后提交审核，服务端会检查资料完整性。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "提交成功"),
            @ApiResponse(code = 400, message = "资料不完整或当前状态不允许提交"),
            @ApiResponse(code = 404, message = "用户不存在"),
            @ApiResponse(code = 500, message = "提交资质审核失败")
    })
    public ResponseResult<String> submitQualification(
            @ApiParam(value = "陪诊师用户ID", required = true, example = "21")
            @PathVariable Integer userId,
            @ApiIgnore HttpServletRequest httpRequest) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(httpRequest);
            if (currentUserId == null || !currentUserId.equals(userId)) {
                return new ResponseResult<>(403, "无权提交他人资质", null);
            }
            User existUser = userService.findById(userId);
            if (existUser == null) {
                return ResponseResult.error("用户不存在");
            }
            String result = attendantService.submitQualification(userId);
            return ResponseResult.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("提交资质审核失败，userId={}", userId, e);
            return ResponseResult.error("提交资质审核失败");
        }
    }

    /**
     * 获取待接单订单列表
     */
    @GetMapping("/orders/waiting")
    @ApiOperation(value = "获取待接单订单列表", notes = "返回接单大厅中的待接单订单，可按服务类型、预计时长和基础费用进行筛选。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功，返回分页订单列表"),
            @ApiResponse(code = 500, message = "获取订单列表失败")
    })
    public ResponseResult<PagedResponse<OrderListResponse>> getWaitingOrders(
            @ApiParam(value = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0") Integer page,
            @ApiParam(value = "每页数量", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam(value = "服务类型：1=普通陪诊，2=术后护理，3=急诊陪同，4=上门陪诊", example = "1") @RequestParam(required = false) Integer serviceType,
            @ApiParam(value = "预计时长下限，单位小时，例如 2 表示筛选大于等于 2 小时的订单", example = "2") @RequestParam(required = false) Integer expectedDurationMinHours,
            @ApiParam(value = "基础费用上限，单位元", example = "80") @RequestParam(required = false) BigDecimal orderAmountMax,
            @ApiIgnore HttpServletRequest request) {
        OrderListQueryRequest queryRequest = new OrderListQueryRequest();
        queryRequest.setPage(page);
        queryRequest.setSize(size);
        queryRequest.setOrderStatus(1); // 1=待接单
            if (serviceType != null) queryRequest.setServiceType(serviceType);
            if (expectedDurationMinHours != null) queryRequest.setExpectedDurationMinHours(expectedDurationMinHours);
            if (orderAmountMax != null) queryRequest.setOrderAmountMax(orderAmountMax);
        
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            PagedResponse<OrderListResponse> result = orderService.getWaitingOrdersForAttendant(currentUserId, queryRequest);
            return ResponseResult.success(result);
        } catch (IllegalArgumentException e) {
            return ResponseResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取待接单订单列表失败", e);
            return ResponseResult.error("获取订单列表失败");
        }
    }

    /**
     * 陪诊师接单
     */
    @PostMapping("/orders/{orderId}/accept")
    @ApiOperation(value = "陪诊师接单", notes = "指定陪诊师接收待接单订单。接单成功后订单会进入待服务状态。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "接单成功"),
            @ApiResponse(code = 400, message = "订单状态不允许接单或接单失败"),
            @ApiResponse(code = 500, message = "接单失败")
    })
    public ResponseResult<OrderAcceptResponse> acceptOrder(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "陪诊师ID", required = true, example = "21") @RequestParam Integer attendantId,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            if (currentUserId == null || !currentUserId.equals(attendantId)) {
                return new ResponseResult<>(403, "无权代替他人接单", null);
            }
            String result = orderService.attendantAcceptOrder(orderId, attendantId);
            if ("接单成功".equals(result)) {
                OrderAcceptResponse response = new OrderAcceptResponse();
                response.setOrderId(orderId);
                response.setMessage("接单成功");
                return ResponseResult.success(response);
            } else {
                return ResponseResult.error(result);
            }
        } catch (Exception e) {
            log.error("陪诊师接单失败，订单ID: {}", orderId, e);
            return ResponseResult.error("接单失败");
        }
    }

    @PostMapping("/orders/{orderId}/reject-assigned")
    @ApiOperation(value = "拒绝专属派单", notes = "指定陪诊师可拒绝专属派单，订单会自动回到公共接单大厅。")
    public ResponseResult<String> rejectAssignedOrder(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "拒绝原因", example = "当前时段无法接单") @RequestParam(required = false) String reason,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            String result = orderService.rejectAssignedOrder(orderId, currentUserId, reason);
            if ("订单已释放回接单大厅".equals(result)) {
                return ResponseResult.success(result);
            }
            return ResponseResult.error(result);
        } catch (Exception e) {
            log.error("拒绝专属派单失败，订单ID: {}", orderId, e);
            return ResponseResult.error("拒绝专属派单失败");
        }
    }

    /**
     * 开始服务
     */
    @PostMapping("/orders/{orderId}/start")
    @ApiOperation(value = "开始服务", notes = "将订单状态从待服务变更为服务中，一般在到院或核销通过后调用。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "服务开始成功"),
            @ApiResponse(code = 400, message = "订单状态不允许开始服务"),
            @ApiResponse(code = 500, message = "开始服务失败")
    })
    public ResponseResult<String> startService(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            String result = orderService.startService(orderId, currentUserId);
            if ("服务开始成功".equals(result)) {
                return ResponseResult.success(result);
            } else {
                return ResponseResult.error(result);
            }
        } catch (Exception e) {
            log.error("开始服务失败，订单ID: {}", orderId, e);
            return ResponseResult.error("开始服务失败");
        }
    }

    /**
     * 结束服务
     */
    @PostMapping("/orders/{orderId}/end")
    @ApiOperation(value = "结束服务", notes = "填写实际服务时长并结束服务，订单会进入待确认时长状态。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "服务结束成功"),
            @ApiResponse(code = 400, message = "订单状态不允许结束服务或时长不合法"),
            @ApiResponse(code = 500, message = "结束服务失败")
    })
    public ResponseResult<String> endService(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "实际服务时长，单位小时", required = true, example = "3.5") @RequestParam BigDecimal actualDuration,
            @ApiParam(value = "陪诊师提交时长说明", example = "检查排队较久") @RequestParam(required = false) String attendantTimeRemark,
            @ApiIgnore HttpServletRequest request) {
        try {
            if (actualDuration == null) {
                return ResponseResult.error("实际服务时长不能为空");
            }
            if (actualDuration.compareTo(BigDecimal.valueOf(0.5)) < 0 || actualDuration.compareTo(BigDecimal.valueOf(24)) > 0) {
                return ResponseResult.error("实际服务时长需在0.5~24小时之间");
            }
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            String result = orderService.endService(orderId, currentUserId, actualDuration, attendantTimeRemark);
            if (result != null && (result.startsWith("服务结束成功") || result.startsWith("服务已提交"))) {
                return ResponseResult.success(result);
            } else {
                return ResponseResult.error(result != null ? result : "结束服务失败");
            }
        } catch (Exception e) {
            log.error("结束服务失败，订单ID: {}", orderId, e);
            return ResponseResult.error("结束服务失败");
        }
    }

    /**
     * 更新服务进度（已到院/候诊中/检查中/就诊完成）
     */
    @PostMapping("/orders/{orderId}/service-progress")
    @ApiOperation(value = "更新服务进度", notes = "用于同步陪诊服务中的关键节点：已到院、候诊中、检查中、就诊完成。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "服务进度已更新"),
            @ApiResponse(code = 400, message = "进度步骤不合法或更新失败"),
            @ApiResponse(code = 500, message = "更新服务进度失败")
    })
    public ResponseResult<String> updateServiceProgress(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "服务进度步骤：1=已到院,2=候诊中,3=检查中,4=就诊完成", required = true, example = "2") @RequestParam Integer step,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            String result = orderService.updateServiceProgress(orderId, currentUserId, step);
            if (result != null && result.startsWith("服务进度已更新")) {
                return ResponseResult.success(result);
            } else {
                return ResponseResult.error(result != null ? result : "更新服务进度失败");
            }
        } catch (Exception e) {
            log.error("更新服务进度失败，订单ID: {}", orderId, e);
            return ResponseResult.error("更新服务进度失败");
        }
    }

    /**
     * 获取陪诊师的订单列表
     */
    @GetMapping("/orders")
    @ApiOperation(value = "获取陪诊师订单列表", notes = "查询指定陪诊师名下订单，可按订单状态分页筛选。前端传入字符串 null 时会被视为不筛选。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功，返回分页订单列表"),
            @ApiResponse(code = 500, message = "获取订单列表失败")
    })
    public ResponseResult<PagedResponse<OrderListResponse>> getAttendantOrders(
            @ApiParam(value = "陪诊师ID", required = true, example = "21") @RequestParam Integer attendantId,
            @ApiParam(value = "页码，从 0 开始", example = "0") @RequestParam(defaultValue = "0") Integer page,
            @ApiParam(value = "每页数量", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @ApiParam(value = "订单状态，可传 null 字符串表示不筛选", example = "6") @RequestParam(required = false) String orderStatusStr,
            @ApiIgnore HttpServletRequest request) {
        Integer currentUserId = AuthUtil.getCurrentUserId(request);
        if (currentUserId == null || !currentUserId.equals(attendantId)) {
            return new ResponseResult<>(403, "无权查看他人订单", null);
        }
        Integer orderStatus = null;
        if (orderStatusStr != null && !"null".equals(orderStatusStr)) {
            try {
                orderStatus = Integer.valueOf(orderStatusStr);
            } catch (NumberFormatException e) {
                log.warn("无效的 orderStatus 值：{}", orderStatusStr);
            }
        }

        final Integer finalOrderStatus = orderStatus;
        OrderListQueryRequest queryRequest = new OrderListQueryRequest();
        queryRequest.setPage(page);
        queryRequest.setSize(size);
        queryRequest.setOrderStatus(orderStatus);
        
        try {
            List<Order> allMatchingOrders = orderService.findAllOrders();
            List<OrderListResponse> filteredList = allMatchingOrders.stream()
                .filter(o -> {
                    if (finalOrderStatus != null && !o.getOrderStatus().equals(finalOrderStatus)) return false;
                    return o.getAttendantId() != null && o.getAttendantId().equals(attendantId);
                })
                .map(this::convertToOrderListResponse)
                .collect(Collectors.toList());

            int total = filteredList.size();
            int start = page * size;
            int end = Math.min(start + size, total);
            List<OrderListResponse> pagedList;

            if (start >= total) {
                pagedList = java.util.Collections.emptyList();
            } else {
                pagedList = filteredList.subList(start, end);
            }

            return ResponseResult.success(new PagedResponse<>(pagedList, total, page, size));
        } catch (Exception e) {
            log.error("获取陪诊师订单列表失败", e);
            return ResponseResult.error("获取订单列表失败");
        }
    }
    
    // 辅助方法：转换 Order 为 OrderListResponse（仅接单后填充用户头像）
    private OrderListResponse convertToOrderListResponse(Order order) {
        OrderListResponse res = new OrderListResponse();
        res.setOrderId(order.getOrderId());
        res.setOrderNo(order.getOrderNo());
        res.setHospital(order.getHospital());
        res.setPatientName(order.getPatientName());
        res.setPatientAge(order.getPatientAge());
        res.setPatientSex(order.getPatientSex());
        res.setServiceDate(order.getServiceDate());
        res.setServiceTimeSlot(order.getServiceTimeSlot());
        res.setOrderAmount(order.getOrderAmount());
        res.setBalanceAmount(order.getBalanceAmount());
        res.setRefundAmount(order.getRefundAmount());
        res.setPenaltyAmount(order.getPenaltyAmount());
        res.setSettlementAmount(OrderSettlementCalculator.settlementAmount(order));
        res.setPlatformFeeAmount(OrderSettlementCalculator.platformFee(order));
        res.setAttendantIncomeAmount(OrderSettlementCalculator.attendantIncome(order));
        res.setOrderStatus(order.getOrderStatus());
        res.setOrderStatusDesc(getOrderStatusDesc(order.getOrderStatus()));
        res.setPaymentStatus(order.getPaymentStatus());
        res.setPaymentStatusDesc(order.getPaymentStatus() == 1 ? "已支付" : "待支付");
        res.setPaymentTime(order.getPaymentTime());
        res.setServiceTypeName(order.getServiceContent());
        res.setCreateTime(order.getCreateTime());
        res.setAcceptTime(order.getAcceptTime());
        res.setActualDuration(order.getActualDuration());
        res.setAttendantName(order.getAttendantName());
        if (order.getAttendantId() != null) {
            res.setAttendantId(String.valueOf(order.getAttendantId()));
        }
        res.setSpecialRequirements(order.getSpecialRequirements());
        res.setCustomRequirement(order.getCustomRequirement());
        if (order.getOrderStatus() != null && order.getOrderStatus() >= 2 && order.getUserId() != null) {
            User user = userService.findById(order.getUserId());
            if (user != null && user.getAvatar() != null) {
                res.setUserAvatar(user.getAvatar());
            }
        }
        return res;
    }
    
    private String getOrderStatusDesc(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "待接单";
            case 2 -> "待服务";
            case 3 -> "服务中";
            case 4 -> "待确认时长";
            case 5 -> "平台争议处理中";
            case 6 -> "已完成";
            case 7 -> "已取消";
            case 8 -> "专属派单待确认";
            case 9 -> "待用户补差额";
            case 10 -> "待平台退款";
            default -> "未知";
        };
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/orders/{orderId}")
    @ApiOperation(value = "获取订单详情", notes = "陪诊师端查看单个订单的完整详情。若订单已被接单，还会补充下单用户头像。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 404, message = "订单不存在"),
            @ApiResponse(code = 500, message = "获取订单详情失败")
    })
    public ResponseResult<Order> getOrderDetail(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            Order order = orderService.getOrderById(orderId);
            if (order != null) {
                if (order.getOrderStatus() != null
                        && order.getOrderStatus() >= 2
                        && order.getAttendantId() != null
                        && !order.getAttendantId().equals(currentUserId)) {
                    return ResponseResult.unauthorized("无权限查看该订单");
                }
                if (order.getOrderStatus() != null && order.getOrderStatus() >= 2 && order.getUserId() != null) {
                    User user = userService.findById(order.getUserId());
                    if (user != null && user.getAvatar() != null) {
                        order.setUserAvatar(user.getAvatar());
                    }
                }
                return ResponseResult.success(order);
            } else {
                return ResponseResult.error("订单不存在");
            }
        } catch (Exception e) {
            log.error("获取订单详情失败，订单ID: {}", orderId, e);
            return ResponseResult.error("获取订单详情失败");
        }
    }

    /**
     * 陪诊师取消订单（仅待服务/待核销状态可取消）
     */
    @RequestMapping(value = "/orders/{orderId}/cancel", method = {RequestMethod.POST, RequestMethod.PUT})
    @ApiOperation(value = "陪诊师取消订单", notes = "陪诊师取消已接订单。若预约尚未开始，订单会释放回接单大厅并通知用户；否则直接标记为已取消。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "取消成功"),
            @ApiResponse(code = 400, message = "订单状态不允许取消或参数不合法"),
            @ApiResponse(code = 500, message = "取消订单失败")
    })
    public ResponseResult<String> cancelOrder(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "取消原因", example = "临时无法到院服务") @RequestParam(required = false) String reason,
            @ApiParam(value = "违约金金额，单位元", example = "20.00") @RequestParam(required = false) BigDecimal penaltyAmount,
            @ApiParam(value = "退款金额，单位元", example = "60.00") @RequestParam(required = false) BigDecimal refundAmount,
            @ApiParam(value = "违约金比例，0-1 之间", example = "0.25") @RequestParam(required = false) BigDecimal penaltyRate,
            @RequestBody(required = false) AttendantCancelOrderRequest requestBody,
            @ApiIgnore HttpServletRequest request) {
        try {
            String finalReason = reason;
            BigDecimal finalPenaltyAmount = penaltyAmount;
            BigDecimal finalRefundAmount = refundAmount;
            BigDecimal finalPenaltyRate = penaltyRate;

            if (requestBody != null) {
                if ((finalReason == null || finalReason.trim().isEmpty()) && requestBody.getReason() != null) {
                    finalReason = requestBody.getReason();
                }
                if (finalPenaltyAmount == null) {
                    finalPenaltyAmount = requestBody.getPenaltyAmount();
                }
                if (finalRefundAmount == null) {
                    finalRefundAmount = requestBody.getRefundAmount();
                }
                if (finalPenaltyRate == null) {
                    finalPenaltyRate = requestBody.getPenaltyRate();
                }
            }

            log.info("陪诊师取消订单请求，orderId={}, reason={}, penaltyAmount={}, refundAmount={}, penaltyRate={}",
                    orderId, finalReason, finalPenaltyAmount, finalRefundAmount, finalPenaltyRate);
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            String result = orderService.attendantCancelOrder(orderId, currentUserId, finalReason, finalPenaltyAmount, finalRefundAmount, finalPenaltyRate);
            if (result != null && (result.startsWith("订单已释放") || result.startsWith("订单已取消"))) {
                return ResponseResult.success(result);
            }
            return ResponseResult.error(result != null ? result : "取消订单失败");
        } catch (Exception e) {
            log.error("取消订单失败，订单ID: {}", orderId, e);
            String msg = e.getMessage() != null ? e.getMessage() : "取消订单失败";
            return ResponseResult.error(msg);
        }
    }

    /**
     * 扫描二维码确认服务开始
     */
    @PostMapping("/orders/{orderId}/scan-qr")
    @ApiOperation(value = "扫描二维码确认服务开始", notes = "校验二维码内容后开始服务，二维码内容应为 SERVICE_CONFIRM_{orderId}。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "扫码成功，服务已开始"),
            @ApiResponse(code = 400, message = "二维码无效或订单状态不允许开始服务"),
            @ApiResponse(code = 500, message = "扫码失败")
    })
    public ResponseResult<String> scanQrCode(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "二维码内容", required = true, example = "SERVICE_CONFIRM_62") @RequestParam String qrCodeContent,
            @ApiIgnore HttpServletRequest request) {
        try {
            String expectedQrCode = "SERVICE_CONFIRM_" + orderId;
            if (!expectedQrCode.equals(qrCodeContent)) {
                return ResponseResult.error("二维码无效");
            }
            
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            String result = orderService.startService(orderId, currentUserId);
            if ("服务开始成功".equals(result)) {
                return ResponseResult.success("扫码成功，服务已开始");
            } else {
                return ResponseResult.error(result);
            }
        } catch (Exception e) {
            log.error("扫描二维码失败，订单ID: {}", orderId, e);
            return ResponseResult.error("扫码失败");
        }
    }
    
    /**
     * 陪诊师获取订单评价（仅已完成订单，且当前用户为该订单陪诊师）
     */
    @GetMapping("/orders/{orderId}/evaluation")
    @ApiOperation(value = "陪诊师获取订单评价", notes = "陪诊师查看指定已完成订单的用户评价，仅订单对应陪诊师可访问。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 400, message = "订单尚未完成"),
            @ApiResponse(code = 401, message = "无权限查看该订单评价"),
            @ApiResponse(code = 404, message = "订单不存在"),
            @ApiResponse(code = 500, message = "获取评价失败")
    })
    public ResponseResult<OrderEvaluation> getOrderEvaluation(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            Order order = orderService.getOrderById(orderId);
            if (order == null) {
                return ResponseResult.error("订单不存在");
            }
            if (order.getAttendantId() == null || !order.getAttendantId().equals(currentUserId)) {
                return ResponseResult.unauthorized("无权限查看该订单评价");
            }
            if (order.getOrderStatus() == null || order.getOrderStatus() != 6) {
                return ResponseResult.error("仅已完成的订单可查看评价");
            }
            OrderEvaluation evaluation = evaluationService.getByOrderId(orderId);
            return ResponseResult.success(evaluation);
        } catch (Exception e) {
            log.error("获取订单评价失败, orderId={}", orderId, e);
            return ResponseResult.error("获取评价失败");
        }
    }

    /**
     * 陪诊师回复订单评价
     */
    @PostMapping("/orders/{orderId}/evaluation/reply")
    @ApiOperation(value = "陪诊师回复订单评价", notes = "陪诊师对用户评价进行回复。请求体使用 JSON 格式，字段名固定为 reply。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "回复成功"),
            @ApiResponse(code = 400, message = "回复内容为空或订单状态不允许回复"),
            @ApiResponse(code = 401, message = "无权限回复该订单评价"),
            @ApiResponse(code = 404, message = "订单不存在"),
            @ApiResponse(code = 500, message = "回复失败")
    })
    public ResponseResult<String> replyToEvaluation(
            @ApiParam(value = "订单ID", required = true, example = "62") @PathVariable Integer orderId,
            @ApiParam(value = "回复请求体，示例：{\"reply\":\"感谢您的认可\"}", required = true)
            @RequestBody Map<String, String> body,
            @ApiIgnore HttpServletRequest request) {
        try {
            Integer currentUserId = AuthUtil.getCurrentUserId(request);
            Order order = orderService.getOrderById(orderId);
            if (order == null) {
                return ResponseResult.error("订单不存在");
            }
            if (order.getAttendantId() == null || !order.getAttendantId().equals(currentUserId)) {
                return ResponseResult.unauthorized("无权限回复该订单评价");
            }
            if (order.getOrderStatus() == null || order.getOrderStatus() != 6) {
                return ResponseResult.error("仅已完成的订单可回复评价");
            }
            String replyContent = body != null ? body.get("reply") : null;
            if (replyContent == null || replyContent.trim().isEmpty()) {
                return ResponseResult.error("回复内容不能为空");
            }
            evaluationService.replyToEvaluation(orderId, replyContent.trim());
            return ResponseResult.success("回复成功");
        } catch (IllegalArgumentException e) {
            return ResponseResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("回复订单评价失败, orderId={}", orderId, e);
            return ResponseResult.error("回复失败");
        }
    }

    /**
     * 获取推荐陪诊师列表
     */
    @GetMapping("/recommended")
    @ApiOperation(value = "获取推荐陪诊师列表", notes = "返回首页或导诊页展示用的推荐陪诊师摘要信息列表。")
    @ApiResponses({
            @ApiResponse(code = 200, message = "查询成功"),
            @ApiResponse(code = 500, message = "获取推荐失败")
    })
    public ResponseResult<List<Map<String, Object>>> getRecommendedAttendants() {
        try {
            List<Attendant> attendants = attendantService.findRecommended();
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            
            for (Attendant attendant : attendants) {
                User user = userService.findById(attendant.getUserId());
                if (user != null) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", user.getId());
                    map.put("name", user.getName());
                    map.put("avatar", user.getAvatar());
                    map.put("professionalField", attendant.getProfessionalField());
                    map.put("score", attendant.getScore());
                    map.put("evaluationCount", attendant.getEvaluationCount() == null ? 0 : attendant.getEvaluationCount());
                    map.put("praiseRate", attendant.getPraiseRate() == null ? 0 : attendant.getPraiseRate());
                    map.put("experienceYears", attendant.getExperienceYears());
                    map.put("serviceCount", attendant.getServiceCount() == null ? 0 : attendant.getServiceCount());
                    result.add(map);
                }
            }
            return ResponseResult.success(result);
        } catch (Exception e) {
            log.error("获取推荐陪诊师失败", e);
            return ResponseResult.error("获取推荐失败");
        }
    }

    private AttendantPublicReviewResponse buildPublicReviewResponse(Order order) {
        if (order == null || order.getOrderId() == null) {
            return null;
        }
        OrderEvaluation evaluation = evaluationService.getByOrderId(order.getOrderId());
        if (evaluation == null) {
            return null;
        }

        AttendantPublicReviewResponse response = new AttendantPublicReviewResponse();
        response.setOrderId(order.getOrderId());
        response.setOrderNo(order.getOrderNo());
        response.setRating(evaluation.getRating());
        response.setContent(evaluation.getContent());
        response.setTags(evaluation.getTags());
        response.setAttendantReply(evaluation.getAttendantReply());
        response.setCreateTime(evaluation.getCreateTime());
        response.setServiceDate(order.getServiceDate());
        response.setServiceTypeName(order.getServiceContent());

        User reviewer = evaluation.getUserId() == null ? null : userService.findById(evaluation.getUserId());
        String displayName = reviewer != null ? reviewer.getName() : order.getPatientName();
        response.setReviewerName(maskDisplayName(displayName));
        return response;
    }

    private String maskDisplayName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return "用户**";
        }
        String name = rawName.trim();
        if (name.length() == 1) {
            return name + "*";
        }
        return name.substring(0, 1) + "**";
    }
}
