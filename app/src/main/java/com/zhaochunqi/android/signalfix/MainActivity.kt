package com.zhaochunqi.android.signalfix

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.android.internal.telephony.ICarrierConfigLoader
import com.zhaochunqi.android.signalfix.ui.theme.SignalFixTheme
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            Toast.makeText(this, "需要读取手机状态权限来获取 SIM 卡信息", Toast.LENGTH_SHORT).show()
        }
    }

    private val onRequestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Shizuku permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_PHONE_STATE)
        }

        setContent {
            SignalFixTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
        
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener)
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var shizukuStatus by remember { mutableStateOf("检查中...") }
    var thresholdsInput by remember { mutableStateOf("-110,-100,-90,-80") } // Default example
    var selectedSubId by remember { mutableIntStateOf(-1) }
    var subInfoList by remember { mutableStateOf<List<SubscriptionInfoCompat>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                shizukuStatus = "Shizuku 已运行并授权"
            } else {
                shizukuStatus = "Shizuku 已运行但未授权"
                Shizuku.requestPermission(0)
            }
        } else {
            shizukuStatus = "Shizuku 未运行"
        }
        
        // Load Subs
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subInfoList = sm.activeSubscriptionInfoList?.map { 
                SubscriptionInfoCompat(it.subscriptionId, "${it.displayName} (${it.carrierName})") 
            } ?: emptyList()
            
            if (subInfoList.isNotEmpty()) {
                selectedSubId = subInfoList[0].id
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("状态: $shizukuStatus", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text("选择 SIM 卡:", style = MaterialTheme.typography.titleMedium)
        if (subInfoList.isEmpty()) {
            Text("未找到活动的 SIM 卡 (或未授予权限)")
        } else {
            subInfoList.forEach { sub ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    RadioButton(
                        selected = (sub.id == selectedSubId),
                        onClick = { selectedSubId = sub.id }
                    )
                    Text(
                        text = sub.displayName,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text("KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = thresholdsInput,
            onValueChange = { thresholdsInput = it },
            label = { Text("阈值 (逗号分隔)") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("示例: -110,-100,-90,-80 (数值必须从小到大)", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (selectedSubId == -1) {
                    Toast.makeText(context, "请选择 SIM 卡", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                try {
                    val thresholds = thresholdsInput.split(",").map { it.trim().toInt() }.toIntArray()
                    applyConfig(selectedSubId, thresholds)
                    Toast.makeText(context, "应用成功!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "应用失败: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            },
            enabled = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        ) {
            Text("应用修复")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                 if (selectedSubId == -1) {
                    Toast.makeText(context, "请选择 SIM 卡", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                try {
                    clearConfig(selectedSubId)
                    Toast.makeText(context, "清除成功!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
             enabled = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        ) {
            Text("清除/重置配置")
        }
    }
}

data class SubscriptionInfoCompat(val id: Int, val displayName: String)

fun applyConfig(subId: Int, thresholds: IntArray) {
    val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("carrier_config_loader"))
    val loader = ICarrierConfigLoader.Stub.asInterface(binder)
    
    val bundle = PersistableBundle().apply {
        putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY, thresholds)
    }
    
    loader.overrideConfig(subId, bundle)
}

fun clearConfig(subId: Int) {
    val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("carrier_config_loader"))
    val loader = ICarrierConfigLoader.Stub.asInterface(binder)
    
    loader.overrideConfig(subId, null)
}
