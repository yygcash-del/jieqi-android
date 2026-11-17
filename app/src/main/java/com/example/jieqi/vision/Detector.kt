package com.example.jieqi.vision
import ai.onnxruntime.OrtEnvironment; import ai.onnxruntime.OrtSession

class Detector(modelPath: String) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelPath)
    // TODO: 17 类推理（14 明子 + 红框暗子 + 黑框暗子 + 空位）→ NMS → 帧差事件
}
