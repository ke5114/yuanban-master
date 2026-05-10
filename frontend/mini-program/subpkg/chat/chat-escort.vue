<template>
  <view class="container">
    <view class="chat-header">
      <view class="header-content">
        <view class="header-back header-side" @click="navigateBack">
          <image class="back-icon" src="/static/back.svg" mode="aspectFit"></image>
        </view>
        <view class="header-middle">
          <view class="header-title">
            <view class="title-row">
              <text class="title-text">{{ targetName }}</text>
              <text class="peer-pill">{{ headerMeta.peerLabel }}</text>
            </view>
            <text v-if="headerSubtitle" class="subtitle-text">{{ headerSubtitle }}</text>
          </view>
        </view>
        <view class="header-side header-placeholder-right"></view>
      </view>
    </view>
    <view class="header-placeholder"></view>

    <scroll-view 
      class="chat-list" 
      scroll-y 
      :scroll-top="scrollTop" 
      :scroll-into-view="scrollIntoView" 
      @scrolltolower="loadMoreHistory"
      upper-threshold="50"
      @click="closePanel"
    >
      <view v-if="loadingMore" class="loading-more"><text>加载中...</text></view>

      <view v-for="(msg, index) in messages" :key="msg.id || index" :id="'msg-' + index">
        
        <view v-if="shouldShowTime(index)" class="time-divider">
          <text>{{ formatTimeCenter(msg.createTime) }}</text>
        </view>

        <view class="message-item" :class="{ 'self': msg.senderId === currentUserId }">
          <image
            class="avatar"
            :src="msg.displayAvatar || userPlaceholder"
            @error="onMessageAvatarError(msg)"
            mode="aspectFill"
          ></image>

          <view class="message-content">
            <text v-if="msg.senderId !== currentUserId" class="sender-name">{{ msg.senderName || targetName }}</text>
            
            <view class="bubble-container">
              <text v-if="msg.senderId === currentUserId && msg.status !== 'sending' && msg.status !== 'failed'" 
                class="read-status" :class="{ 'read': msg.isRead }">
                {{ msg.isRead ? '已读' : '未读' }}
              </text>

              <view v-if="msg.senderId === currentUserId && msg.status === 'sending'" class="loading-spinner"></view>
              <view v-if="msg.senderId === currentUserId && msg.status === 'failed'" class="fail-icon">!</view>

              <view class="content-bubble" :class="{'voice-bubble': msg.msgType === 3}" @longpress="selectReplyTarget(msg)">
                <view v-if="msg.replySummary" class="reply-quote">
                  <text class="reply-quote-text">{{ msg.replySummary }}</text>
                </view>
                <!-- 文本 -->
                <text v-if="msg.msgType === 1" class="text">{{ msg.displayContent }}</text>
                <!-- 图片 -->
                <image v-else-if="msg.msgType === 2" class="image" :src="getImageUrl(msg.content)" mode="widthFix" @click="previewImage(getImageUrl(msg.content))"></image>
                <!-- 语音 -->
                <view v-else-if="msg.msgType === 3" class="voice-content" @click="playVoice(msg.content)">
                  <image class="voice-icon-img" :src="voice" mode="aspectFit"></image>
                  <text class="voice-text">语音消息</text>
                </view>
                <!-- 位置 -->
                <view v-else-if="msg.msgType === 4" class="location-content" @click="openLocation(msg.content)">
                  <image class="location-icon-img" :src="location" mode="aspectFit"></image>
                  <view class="location-text-wrap">
                    <text class="location-name">{{ parseLocation(msg.content).name || '位置信息' }}</text>
                  </view>
                </view>
                <!-- 其他 -->
                <text v-else class="text">[未知消息类型]</text>
              </view>
            </view>
          </view>
        </view>
      </view>
      
      <view class="bottom-placeholder" :style="{ height: (showPanel ? 500 : 0) + 140 + 'rpx' }"></view>
    </scroll-view>

    <!-- 底部输入区域 -->
    <view class="footer-area" :style="{ bottom: keyboardHeight + 'px' }">
        <view v-if="replyTarget" class="reply-preview">
          <view class="reply-preview-main">
            <text class="reply-preview-label">回复 {{ replyTarget.senderId === currentUserId ? '自己' : (replyTarget.senderName || targetName) }}</text>
            <text class="reply-preview-text">{{ replyTarget.contentPreview }}</text>
          </view>
          <text class="reply-preview-close" @click="clearReplyTarget">×</text>
        </view>
        <view class="input-toolbar">
            <!-- 语音切换 -->
            <view class="icon-btn" @click="switchVoiceMode">
                <image class="icon-img" :src="isVoiceMode ? keyboard : voice" mode="aspectFit"></image>
            </view>

            <!-- 输入框/按住说话 -->
            <view class="input-wrapper">
                <view v-if="isVoiceMode" class="voice-record-btn" :class="{ 'recording': recording }"
                    @touchstart="startRecord" @touchend="stopRecord" @touchcancel="cancelRecord">
                    <text>{{ recording ? '松开 结束' : '按住 说话' }}</text>
                </view>
                <input v-else class="input" v-model="inputText" confirm-type="send" @confirm="sendText"
                    :adjust-position="false" @focus="onInputFocus" @blur="onInputBlur" cursor-spacing="20" />
            </view>

            <!-- 表情 -->
            <view class="icon-btn" @click="toggleEmoji">
                <image class="icon-img" :src="emoji" mode="aspectFit"></image>
            </view>

            <!-- 发送/更多 -->
            <view class="action-btn">
                <view v-if="inputText.trim() && !isVoiceMode" class="send-btn" :class="{ 'sending': isSending }" @click="sendText">
                    <text>{{ isSending ? '发送中...' : '发送' }}</text>
                </view>
                <view v-else class="icon-btn" @click="toggleMore">
                    <image class="icon-img" :src="plus" mode="aspectFit"></image>
                </view>
            </view>
        </view>

        <!-- 功能面板 -->
        <view class="panel-area" v-if="showPanel">
            <!-- 表情面板 -->
            <scroll-view scroll-y v-if="panelType === 'emoji'" class="emoji-panel">
                <view class="emoji-grid">
                    <view v-for="(emoji, index) in emojiList" :key="index" class="emoji-item" @click="addEmoji(emoji)">
                        {{ emoji }}
                    </view>
                </view>
            </scroll-view>

            <!-- 更多功能面板 -->
            <view v-if="panelType === 'more'" class="more-panel">
                <view class="more-item" @click="chooseImage('album')">
                    <view class="more-icon-box">
                        <image class="more-icon-img" :src="album" mode="aspectFit"></image>
                    </view>
                    <text class="more-text">相册</text>
                </view>
                <view class="more-item" @click="chooseImage('camera')">
                    <view class="more-icon-box">
                        <image class="more-icon-img" :src="camera" mode="aspectFit"></image>
                    </view>
                    <text class="more-text">拍摄</text>
                </view>
                <view class="more-item" @click="chooseLocation">
                    <view class="more-icon-box">
                        <image class="more-icon-img" :src="location" mode="aspectFit"></image>
                    </view>
                    <text class="more-text">位置</text>
                </view>
                <view class="more-item" @click="sendEmergency">
                    <view class="more-icon-box">
                        <image class="more-icon-img" :src="emergency" mode="aspectFit"></image>
                    </view>
                    <text class="more-text">紧急</text>
                </view>
                <view class="more-item" @click="videoCall">
                    <view class="more-icon-box">
                        <image class="more-icon-img" :src="video" mode="aspectFit"></image>
                    </view>
                    <text class="more-text">视频</text>
                </view>
                 <view class="more-item" @click="voiceCall">
                    <view class="more-icon-box">
                        <image class="more-icon-img" :src="call" mode="aspectFit"></image>
                    </view>
                    <text class="more-text">通话</text>
                </view>
            </view>
        </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { get, post, upload } from '@/utils/api.js'
import { addChatListener, removeChatListener, connectChatSocket } from '@/utils/chat-websocket.js'
import { useMessageStore } from '@/stores/message.js'
import {
  chooseLocationWithGuard,
  createInnerAudioContext,
  createRecorderManager,
  openLocationWithGuard,
  showUnsupportedFeature
} from '@/subpkg/common/runtime.js'
import { album, call, camera, doctorAvatar, emoji, emergency, keyboard, location, plus, userPlaceholder, video, voice } from '@/utils/assets.js'
import { resolveAvatarUrl, resolveImageUrl } from '@/utils/media.js'
import { redirectPublicSafeToHome } from '@/utils/site-mode.js'
import { buildChatHeaderMeta } from '@/utils/chat-ui.mjs'

const currentUserId = ref(uni.getStorageSync('userInfo')?.id || 0)
const targetUserId = ref(null)
const targetName = ref(buildChatHeaderMeta({ role: 'escort' }).peerLabel)
const targetAvatar = ref(doctorAvatar)
const messageStore = useMessageStore()
const messages = ref([])
const inputText = ref('')
const scrollTop = ref(0)
const scrollIntoView = ref('')
const loadingMore = ref(false)
const hasMoreHistory = ref(true)
const pageSize = 20
const currentPage = ref(1)
const isSending = ref(false)
const readSyncing = ref(false)
const replyTarget = ref(null)

// 新增状态
const isVoiceMode = ref(false)
const showPanel = ref(false)
const panelType = ref('') // 'emoji' | 'more'
const keyboardHeight = ref(0)
const recording = ref(false)
const recorderManager = createRecorderManager()
const innerAudioContext = createInnerAudioContext()
// 消息去重缓存
const processedMessages = new Set()

const emojiList = ['😀','😁','😂','🤣','😃','😄','😅','😆','😉','😊','😋','😎','😍','😘','🥰','😗','😙','😚','🙂','🤗','🤩','🤔','🤨','😐','😑','😶','🙄','😏','😣','😥','😮','🤐','😯','😪','😫','😴','😌','😛','😜','😝','🤤','😒','😓','😔','😕','🙃','🤑','😲','☹️','🙁','😖','😞','😟','😤','😢','😭','😦','😧','😨','😩','🤯','😬','😰','😱','🥵','🥶','😳','🤪','😵','😡','😠','🤬','😷','🤒','🤕','🤢','🤮','🤧','😇','🤠','🤡','🥳','🥴','🥺','🤥','🤫','🤭','🧐','🤓','😈','👿']
const isReadReceiptMessage = (msg = {}) => String(msg.type || '') === 'READ_RECEIPT' || Number(msg.msgType || 0) === 99

const parseDateTimeSafe = (value) => {
  if (!value) return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  const normalized = typeof value === 'string' ? value.replace(/-/g, '/') : value
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

const getTimestamp = (value) => {
  const parsed = parseDateTimeSafe(value)
  return parsed ? parsed.getTime() : 0
}

const headerMeta = computed(() => buildChatHeaderMeta({ role: 'escort' }))
const headerSubtitle = computed(() => headerMeta.value.subtitle)

onLoad((options) => {
  if (redirectPublicSafeToHome()) return
  uni.stopPullDownRefresh()

  if (!options.userId && !options.attendantId) { uni.navigateBack(); return; }
  targetUserId.value = parseInt(options.userId || options.attendantId)
  targetName.value = options.name ? decodeURIComponent(options.name) : headerMeta.value.peerLabel
  if (options.avatar) targetAvatar.value = resolveAvatarUrl(decodeURIComponent(options.avatar), doctorAvatar)

  connectChatSocket()
  loadHistory()

  // 监听键盘高度
  uni.onKeyboardHeightChange(res => {
      if (res.height > 0) {
          keyboardHeight.value = res.height
          showPanel.value = false // 键盘弹起时隐藏面板
          scrollToBottom()
      } else {
          keyboardHeight.value = 0
      }
  })

  // 录音监听
  if (recorderManager) {
      recorderManager.onStop((res) => {
          if (recording.value) {
              sendVoice(res.tempFilePath)
              recording.value = false
          }
      })
  }
})

onMounted(() => addChatListener(handleNewMessage))
onUnmounted(() => {
  removeChatListener(handleNewMessage)
  if (innerAudioContext && typeof innerAudioContext.destroy === 'function') {
    innerAudioContext.destroy()
  }
})

const shouldShowTime = (index) => {
  if (index === 0) return true
  const prevTime = getTimestamp(messages.value[index - 1].createTime)
  const currTime = getTimestamp(messages.value[index].createTime)
  if (!prevTime || !currTime) return true
  return (currTime - prevTime) > 5 * 60 * 1000
}

const loadHistory = async () => {
  try {
    const res = await get(`/api/chat/history?targetUserId=${targetUserId.value}&page=1&pageSize=${pageSize}`)
    if (res.code === 200) {
      messages.value = res.data.map(normalizeChatMessage)
      hasMoreHistory.value = res.data.length === pageSize
      setTimeout(() => scrollToBottom(), 100)
      markAsRead(true)
    }
  } catch (e) {}
}

const loadMoreHistory = async () => {
  if (loadingMore.value || !hasMoreHistory.value) return
  loadingMore.value = true
  try {
    currentPage.value++
    const res = await get(`/api/chat/history?targetUserId=${targetUserId.value}&page=${currentPage.value}&pageSize=${pageSize}`)
    if (res.code === 200 && res.data.length > 0) {
      messages.value = [...res.data.reverse().map(normalizeChatMessage), ...messages.value]
      hasMoreHistory.value = res.data.length === pageSize
    } else {
      hasMoreHistory.value = false
    }
  } catch (e) { currentPage.value-- } finally { loadingMore.value = false }
}

const handleNewMessage = (msg) => {
  console.log('处理新消息:', msg)
  
  // 消息去重处理
  const msgKey = `${msg.senderId}-${msg.receiverId}-${msg.createTime}-${msg.content}`;
  if (processedMessages.has(msgKey)) {
    console.log('消息已处理，跳过:', msgKey);
    return;
  }
  processedMessages.add(msgKey);
  
  if (isReadReceiptMessage(msg)) {
    applyReadReceipt(msg)
    return
  }

  // 严格判断普通聊天消息类型，排除其他系统消息
  const isNormalChatMsg = [1, 2, 3, 4].includes(Number(msg.msgType)) && msg.type !== 'MESSAGE_STATUS_UPDATE';

  if (isNormalChatMsg && (msg.senderId == targetUserId.value || msg.receiverId == targetUserId.value)) {
    if (msg.senderId == targetUserId.value) {
        // 来自对方的消息
        const newMsg = { ...msg };
        
        // 关键修复：确保消息包含发送者头像
        if (!newMsg.senderAvatar || newMsg.senderAvatar === userPlaceholder) {
            // 使用预加载的目标用户头像
            newMsg.senderAvatar = targetAvatar.value || userPlaceholder;
            console.log('设置发送者头像:', newMsg.senderAvatar);
        }
        
        // 避免重复添加相同ID的消息
        if (!messages.value.some(m => m.id == newMsg.id)) {
            messages.value.push(normalizeChatMessage(newMsg));
            scrollToBottom();
            markAsRead(true);
        }
    } else if (msg.receiverId == targetUserId.value) {
        // 自己发送的消息回显
        if (!messages.value.some(m => m.id == msg.id)) {
            messages.value.push(normalizeChatMessage(msg));
            scrollToBottom();
        }
    }
  }

  // 处理其他类型的状态更新消息
  if (msg.type === 'MESSAGE_STATUS_UPDATE') {
    console.log('收到消息状态更新:', msg);
    const messageId = msg.messageId || msg.id;
    if (messageId) {
      messages.value = messages.value.map(m => {
        if (m.id == messageId) {
          return { ...m, ...msg.updates };
        }
        return m;
      });
    }
    return; // 状态更新消息不添加到列表
  }
}

const applyReadReceipt = (msg) => {
  console.log('收到已读回执，更新消息状态', msg)
  const lastReadMessageId = Number(msg.lastReadMessageId || 0)
  const readUpToTime = getTimestamp(msg.readUpToTime)
  messages.value = messages.value.map((item) => {
    if (item.senderId != currentUserId.value || item.receiverId != targetUserId.value) return item
    if (item.status === 'sending' || item.status === 'failed') return item
    const itemId = Number(item.id || 0)
    const itemTime = getTimestamp(item.createTime)
    const shouldMarkRead = (lastReadMessageId && itemId && itemId <= lastReadMessageId) || (readUpToTime && itemTime && itemTime <= readUpToTime)
    return shouldMarkRead ? { ...item, isRead: 1 } : item
  })
}

// 发送逻辑封装
const sendMessage = async (content, type) => {
    if (isSending.value) return
    isSending.value = true
    
    const userInfo = uni.getStorageSync('userInfo')
    const tempMsg = {
      id: 'temp-' + Date.now(), senderId: currentUserId.value, receiverId: targetUserId.value, content: content, msgType: type,
      senderName: userInfo?.name || '我', senderAvatar: userInfo?.avatar, createTime: new Date(), status: 'sending', isRead: 0
    }
    messages.value.push(normalizeChatMessage(tempMsg))
    scrollToBottom()
    const tempIndex = messages.value.length - 1

    try {
      const res = await post('/api/chat/send', { receiverId: targetUserId.value, content: content, msgType: type })
      if (res.code === 200) messages.value[tempIndex] = normalizeChatMessage({ ...res.data, status: 'sent' })
      else throw new Error('Failed')
    } catch (e) { messages.value[tempIndex].status = 'failed' }
    finally {
      isSending.value = false
    }
}

const sendText = () => {
  const pureText = inputText.value.trim()
  if (!pureText || isSending.value) return
  const payload = replyTarget.value ? `[reply:${replyTarget.value.id}] ${pureText}` : pureText
  sendMessage(payload, 1)
  inputText.value = ''
  clearReplyTarget()
}

const selectReplyTarget = (msg) => {
  if (!msg) return
  const contentPreview = buildContentPreview(msg)
  replyTarget.value = {
    id: msg.id,
    senderId: msg.senderId,
    senderName: msg.senderName,
    contentPreview
  }
}

const clearReplyTarget = () => {
  replyTarget.value = null
}

const buildContentPreview = (msg = {}) => {
  if (Number(msg.msgType) === 2) return '[图片]'
  if (Number(msg.msgType) === 3) return '[语音]'
  if (Number(msg.msgType) === 4) return '[位置]'
  return String(msg.content || '').slice(0, 40)
}

const parseReplyPayload = (content = '') => {
  const raw = String(content || '')
  const matched = raw.match(/^\[reply:(\d+)\]\s*/)
  if (!matched) return { replyId: null, text: raw }
  return { replyId: Number(matched[1]), text: raw.slice(matched[0].length) }
}


const chooseImage = (sourceType) => {
  uni.chooseImage({
    count: 1,
    sourceType: sourceType ? [sourceType] : ['album', 'camera'],
    success: async (res) => {
      const path = res.tempFilePaths[0]
      // 先上传
      try {
        const uploadRes = await upload('/api/common/upload', path)
        if (uploadRes.code === 200) {
            sendMessage(uploadRes.url, 2)
        }
      } catch (e) { console.error(e) }
    }
  })
}

const sendVoice = async (path) => {
    try {
        const uploadRes = await upload('/api/common/upload', path)
        if (uploadRes.code === 200) {
            sendMessage(uploadRes.url, 3)
        }
    } catch (e) { console.error(e) }
}

const chooseLocation = () => {
    chooseLocationWithGuard({
        success: (res) => {
            const locationData = JSON.stringify({
                name: res.name,
                address: res.address,
                latitude: res.latitude,
                longitude: res.longitude
            })
            sendMessage(locationData, 4)
        }
    })
}

const sendEmergency = () => {
    sendMessage("【紧急求助】请立即联系我！", 1)
}

const videoCall = () => {
    uni.showToast({ title: '视频通话功能开发中', icon: 'none' })
}

const voiceCall = () => {
    uni.showToast({ title: '语音通话功能开发中', icon: 'none' })
}

// 交互逻辑
const switchVoiceMode = () => {
    if (!isVoiceMode.value && !recorderManager) {
        showUnsupportedFeature('录音', '公网 IP 的 HTTP 页面不支持录音，请改用小程序或 HTTPS')
        return
    }
    isVoiceMode.value = !isVoiceMode.value
    if (isVoiceMode.value) {
        showPanel.value = false
        uni.hideKeyboard()
    } else {
        // 切换回键盘，自动聚焦
        nextTick(() => {
            // 实际开发中可能需要手动 focus
        })
    }
}

const toggleEmoji = () => {
    if (panelType.value === 'emoji' && showPanel.value) {
        showPanel.value = false
        // 切换回键盘
    } else {
        panelType.value = 'emoji'
        showPanel.value = true
        isVoiceMode.value = false
        uni.hideKeyboard()
        scrollToBottom()
    }
}

const toggleMore = () => {
    if (panelType.value === 'more' && showPanel.value) {
        showPanel.value = false
    } else {
        panelType.value = 'more'
        showPanel.value = true
        isVoiceMode.value = false
        uni.hideKeyboard()
        scrollToBottom()
    }
}

const closePanel = () => {
    showPanel.value = false
    uni.hideKeyboard()
}

const onInputFocus = (e) => {
    showPanel.value = false
    keyboardHeight.value = e.detail.height
    scrollToBottom()
}

const onInputBlur = () => {
    keyboardHeight.value = 0
}

const addEmoji = (emoji) => {
    inputText.value += emoji
}

const startRecord = () => {
    if (!recorderManager) {
        showUnsupportedFeature('录音', '公网 IP 的 HTTP 页面不支持录音，请改用小程序或 HTTPS')
        return
    }
    recording.value = true
    recorderManager.start()
}

const stopRecord = () => {
    if (!recorderManager) return
    // 录音结束在 onStop 中处理
    recorderManager.stop()
}

const cancelRecord = () => {
    recording.value = false
    if (!recorderManager) return
    recorderManager.stop() // 需要标记不发送
}

const playVoice = (url) => {
    if (!innerAudioContext) {
        showUnsupportedFeature('语音播放', '当前环境不支持语音播放')
        return
    }
    innerAudioContext.src = getImageUrl(url)
    innerAudioContext.play()
}

const openLocation = (content) => {
    try {
        const loc = JSON.parse(content)
        openLocationWithGuard({
            latitude: loc.latitude,
            longitude: loc.longitude,
            name: loc.name,
            address: loc.address
        })
    } catch (e) {}
}

const parseLocation = (content) => {
    try { return JSON.parse(content) } catch(e) { return {} }
}

const markAsRead = async (silent = false) => {
  if (!targetUserId.value || readSyncing.value) return
  messageStore.updateContactUnread(targetUserId.value, 0)
  messageStore.updateTabBarBadge()
  readSyncing.value = true
  try {
    await post(`/api/chat/read?senderId=${targetUserId.value}`)
    messageStore.scheduleRefreshUnreadCounts(120)
  } catch (error) {
    console.error('sync escort read status failed', error)
    messageStore.scheduleRefreshUnreadCounts(120)
    if (!silent) {
      uni.showToast({ title: '已读状态同步失败', icon: 'none' })
    }
  } finally {
    readSyncing.value = false
  }
}
const navigateBack = () => {
  // 返回前通知消息页面更新状态
  uni.$emit('chat:return', { targetUserId: targetUserId.value })
  uni.navigateBack()
}
const scrollToBottom = () => { nextTick(() => { scrollIntoView.value = 'msg-' + (messages.value.length - 1) }) }
const getImageUrl = (url) => resolveImageUrl(url, userPlaceholder)
const previewImage = (url) => uni.previewImage({ urls: [url], current: url })

const normalizeChatMessage = (msg) => {
    if (!msg) return msg
    const userInfo = uni.getStorageSync('userInfo') || {}
    const currentUserAvatar = userInfo.avatarUrl || userInfo.avatar || ''
    const displayAvatar = msg.senderId === currentUserId.value
        ? resolveAvatarUrl(currentUserAvatar, userPlaceholder)
        : resolveAvatarUrl(msg.senderAvatar || targetAvatar.value || '', userPlaceholder)
    const parsedReply = Number(msg.msgType) === 1 ? parseReplyPayload(msg.content) : { replyId: null, text: msg.content }
    const replySource = parsedReply.replyId ? messages.value.find(item => Number(item.id) === parsedReply.replyId) : null
    const replySummary = replySource ? `${replySource.senderId === currentUserId.value ? '你' : (replySource.senderName || targetName.value)}：${buildContentPreview(replySource)}` : ''
    return {
        ...msg,
        displayAvatar,
        displayContent: parsedReply.text,
        replySummary
    }
}

const onMessageAvatarError = (msg) => {
    if (!msg) return
    msg.displayAvatar = userPlaceholder
}

const formatTimeCenter = (time) => {
  const d = parseDateTimeSafe(time)
  if (!d) return ''
  const now = new Date()
  if (d.toDateString() === now.toDateString()) return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`
  return `${d.getMonth()+1}月${d.getDate()}日 ${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`
}
</script>

<style lang="scss" scoped>
@import '@/styles/escort-ui.scss';
$primary-color: $escort-color-primary;
$primary-deep: $escort-color-primary-deep;
$bg-color: #f5f8fb;
$text-main: #1f2937;
$bubble-other: #fff;
$bubble-self: $primary-color;

.container {
  height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f6fafc 150rpx, $bg-color 100%);
  display: flex;
  flex-direction: column;
}

.chat-header {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 100;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8rpx);
  padding-top: var(--status-bar-height);
  border-bottom: 1rpx solid #e4edf5;
}

.header-content {
  height: 88rpx;
  display: grid;
  grid-template-columns: 72rpx 1fr 72rpx;
  align-items: center;
  column-gap: 8rpx;
  padding: 0 22rpx;
}

.header-side {
  width: 72rpx;
  min-width: 72rpx;
  max-width: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-back {
  height: 72rpx;
  border-radius: 36rpx;
  justify-self: start;
}

.back-icon {
  width: 38rpx;
  height: 38rpx;
}

.header-middle {
  min-width: 0;
  width: 100%;
  display: flex;
  justify-content: center;
}

.header-title {
  min-width: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  text-align: center;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  max-width: 100%;
  width: 100%;
}

.title-text {
  font-size: 31rpx;
  font-weight: 700;
  color: $text-main;
  max-width: 460rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.peer-pill {
  flex-shrink: 0;
  max-width: 132rpx;
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(0, 122, 255, 0.08);
  border: 1rpx solid rgba(0, 122, 255, 0.14);
  color: $primary-deep;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subtitle-text {
  font-size: 22rpx;
  color: #8491a3;
  max-width: 520rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-placeholder-right {
  visibility: hidden;
}
.header-placeholder { width: 100%; height: calc(88rpx + var(--status-bar-height)); flex-shrink: 0; }

.chat-list { flex: 1; width: 100%; box-sizing: border-box; padding: 24rpx; overflow-y: scroll; -webkit-overflow-scrolling: touch; }
.time-divider { display: flex; justify-content: center; margin: 32rpx 0; text { font-size: 22rpx; color: #999; background: rgba(0,0,0,0.05); padding: 4rpx 16rpx; border-radius: 8rpx; } }
.loading-more { text-align: center; padding: 10rpx; font-size: 22rpx; color: #999; }

.message-item { display: flex; margin-bottom: 30rpx; align-items: flex-start; &.self { flex-direction: row-reverse; } }
.avatar { width: 80rpx; height: 80rpx; border-radius: 12rpx; flex-shrink: 0; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05); background: #fff; }
.message-content { display: flex; flex-direction: column; max-width: 70%; margin: 0 20rpx; .self & { align-items: flex-end; } }
.sender-name { font-size: 22rpx; color: #999; margin-bottom: 4rpx; margin-left: 8rpx; }

.bubble-container { display: flex; align-items: flex-end; gap: 10rpx; .self & { flex-direction: row-reverse; } }
.read-status { font-size: 20rpx; color: #999; margin-bottom: 10rpx; white-space: nowrap; &.read { color: #999; } }
.loading-spinner { width: 24rpx; height: 24rpx; border: 2rpx solid rgba(102,166,255,0.3); border-top-color: $primary-color; border-radius: 50%; animation: spin 1s linear infinite; margin-bottom: 10rpx; }
.fail-icon { width: 30rpx; height: 30rpx; background: #ff4d4f; color: #fff; border-radius: 50%; font-size: 20rpx; display: flex; align-items: center; justify-content: center; margin-bottom: 10rpx; }
@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

.content-bubble {
  position: relative; padding: 20rpx 24rpx; border-radius: 12rpx; font-size: 30rpx; line-height: 1.5; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.06);
  background: $bubble-other; color: $text-main;
  .self & { background: linear-gradient(135deg, #69B2FF 0%, #007AFF 55%, #2563EB 100%); color: #fff; }
  .image { max-width: 300rpx; border-radius: 8rpx; display: block; }
}

.voice-content {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.voice-icon-img {
  width: 32rpx;
  height: 32rpx;
}
.location-content {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.location-icon-img {
  width: 32rpx;
  height: 32rpx;
}
.location-name {
  font-size: 28rpx;
  color: $text-main;
}

.bottom-placeholder { height: 140rpx; transition: height 0.3s; }

/* 底部区域样式 */
.footer-area {
    position: fixed; left: 0; width: 100%; z-index: 100;
    background: #fff; border-top: 1rpx solid #E5E5E5;
    padding-bottom: constant(safe-area-inset-bottom);
    padding-bottom: env(safe-area-inset-bottom);
    transition: bottom 0.1s;
    box-shadow: 0 -2rpx 12rpx rgba(0,0,0,0.05);
}

.input-toolbar {
    display: flex; align-items: center; padding: 16rpx 20rpx;
    min-height: 100rpx; box-sizing: border-box;
}

.input-wrapper {
    flex: 1; margin: 0 20rpx;
}

.input {
    width: 100%; height: 72rpx; background: #F5F7FA; border-radius: 12rpx;
    padding: 0 20rpx; font-size: 30rpx; box-sizing: border-box; border: 1rpx solid transparent;
    &:focus { background: #fff; border-color: $primary-color; }
}

.voice-record-btn {
    width: 100%; height: 72rpx; background: linear-gradient(135deg, #69B2FF 0%, #007AFF 55%, #2563EB 100%); border-radius: 12rpx;
    display: flex; align-items: center; justify-content: center;
    font-size: 30rpx; color: #fff; font-weight: 500;
    &.recording { background: linear-gradient(135deg, #007AFF 0%, #2563EB 100%); }
}

.icon-btn {
    width: 60rpx; height: 60rpx; display: flex; align-items: center; justify-content: center;
}
.icon-img {
    width: 56rpx; height: 56rpx;
}

.action-btn {
    width: 100rpx; display: flex; align-items: center; justify-content: center; margin-left: 10rpx;
}

.send-btn {
    background: linear-gradient(135deg, #69B2FF 0%, #007AFF 55%, #2563EB 100%); color: #fff; padding: 10rpx 24rpx; border-radius: 12rpx; font-size: 26rpx; font-weight: 500;
    box-shadow: 0 4rpx 12rpx rgba(102,166,255,0.3);
    &.sending { background: linear-gradient(135deg, #b7d8ff 0%, #95c3ff 100%); box-shadow: 0 2rpx 8rpx rgba(102,166,255,0.2); }
}

.reply-preview {
  margin: 14rpx 20rpx 0;
  padding: 14rpx 18rpx;
  border-left: 6rpx solid $primary-color;
  background: #f4f8ff;
  border-radius: 12rpx;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}
.reply-preview-main { min-width: 0; flex: 1; }
.reply-preview-label { display:block; font-size: 22rpx; color:#5b6b85; margin-bottom: 6rpx; }
.reply-preview-text { display:block; font-size: 24rpx; color: $text-main; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reply-preview-close { font-size: 36rpx; line-height: 1; color:#8aa0c2; }
.reply-quote { margin-bottom: 8rpx; padding: 8rpx 12rpx; background: rgba(0,0,0,0.06); border-radius: 8rpx; }
.reply-quote-text { font-size: 22rpx; color: inherit; opacity: 0.85; }

/* 面板区域 */
.panel-area {
    height: 500rpx; background: #fff; border-top: 1rpx solid #E5E5E5;
    overflow: hidden;
}

.emoji-panel {
    height: 100%; padding: 20rpx; box-sizing: border-box;
}
.emoji-grid {
    display: flex; flex-wrap: wrap;
}
.emoji-item {
    width: 12.5%; height: 80rpx; display: flex; align-items: center; justify-content: center; font-size: 40rpx;
}

.more-panel {
    height: 100%; display: flex; flex-wrap: wrap; padding: 40rpx 30rpx; box-sizing: border-box;
}
.more-item {
    width: 25%; display: flex; flex-direction: column; align-items: center; margin-bottom: 40rpx;
}
.more-icon-box {
    width: 110rpx; height: 110rpx; background: linear-gradient(135deg, #f5f7fa 0%, #edf3fb 100%); border-radius: 24rpx;
    display: flex; align-items: center; justify-content: center; margin-bottom: 10rpx;
    box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05);
}
.more-icon-img {
    width: 64rpx; height: 64rpx;
}
.more-text {
    font-size: 24rpx; color: #666;
}

.voice-content {
    display: flex; align-items: center; gap: 10rpx; min-width: 120rpx;
}
.location-content {
    display: flex; align-items: center; gap: 10rpx;
}
</style>
