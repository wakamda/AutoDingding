package com.pengxh.autodingding.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.gyf.immersionbar.ImmersionBar
import com.pengxh.autodingding.R
import com.pengxh.autodingding.adapter.BaseFragmentAdapter
import com.pengxh.autodingding.databinding.ActivityMainBinding
import com.pengxh.autodingding.extensions.isAppAvailable
import com.pengxh.autodingding.fragment.DingDingFragment
import com.pengxh.autodingding.fragment.SettingsFragment
import com.pengxh.autodingding.utils.Constant
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.show
import com.pengxh.kt.lite.utils.ActivityStackManager
import com.pengxh.kt.lite.utils.SaveKeyValues
import com.pengxh.kt.lite.widget.dialog.AlertMessageDialog
import org.eclipse.paho.client.mqttv3.*
//import org.eclipse.paho.android.service.MqttAndroidClient
import info.mqtt.android.service.MqttAndroidClient;
import android.net.ConnectivityManager
import android.util.Log
import android.content.IntentFilter
import com.pengxh.autodingding.utils.NetworkUtils
import info.mqtt.android.service.Ack


class MainActivity : KotlinBaseActivity<ActivityMainBinding>() {

    private var menuItem: MenuItem? = null
    private var clickTime: Long = 0

    //mqtt set
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var mqttClient: MqttAndroidClient
    private val mqttServerUrl = "tcp://39.106.230.248:1883" // 替换为你的 MQTT 服务器地址
    private val mqttClientId = "DingDingDarkPhone_AE86" // 为客户端生成唯一ID

    private val user = "DingDingDarkPhone"
    private val pwd = "003025@Chengtao25"

    private val mqttTopic1 = "/topic/DarkTest" // 订阅的主题
    private val mqttTopic2 = "/topic/Dark" // 订阅的主题
    private val mqttTopic3 = "/topic/test3" // 订阅的主题
    private val mqttTopic4 = "/topic/test4" // 订阅的主题

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun setupTopBarLayout() {
        ImmersionBar.with(this).statusBarDarkFont(true).init()
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        ActivityStackManager.addActivity(this)

        if (!isAppAvailable(Constant.DING_DING)) {
            showAlertDialog()
            return
        }

        val fragmentPages = ArrayList<Fragment>()
        fragmentPages.add(DingDingFragment())
        fragmentPages.add(SettingsFragment())

        val fragmentAdapter = BaseFragmentAdapter(supportFragmentManager, fragmentPages)
        binding.viewPager.adapter = fragmentAdapter
        binding.viewPager.offscreenPageLimit = fragmentPages.size

        // 网络变化接收器初始化
        networkChangeReceiver = NetworkChangeReceiver(this)

        val isFirst = SaveKeyValues.getValue("isFirst", true) as Boolean
        if (isFirst) {
            AlertMessageDialog.Builder()
                .setContext(this)
                .setTitle("温馨提醒")
                .setMessage("本软件仅供内部使用，严禁商用或者用作其他非法用途")
                .setPositiveButton("知道了")
                .setOnDialogButtonClickListener(object :
                    AlertMessageDialog.OnDialogButtonClickListener {
                    override fun onConfirmClick() {
                        SaveKeyValues.putValue("isFirst", false)
                    }
                }).build().show()
        }
    }

    override fun initEvent() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val itemId: Int = item.itemId
            if (itemId == R.id.nav_dingding) {
                if (isAppAvailable(Constant.DING_DING)) {
                    binding.viewPager.currentItem = 0
                } else {
                    showAlertDialog()
                }
            } else if (itemId == R.id.nav_settings) {
                binding.viewPager.currentItem = 1
            }
            false
        }

        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int, positionOffset: Float, positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                if (menuItem != null) {
                    menuItem!!.isChecked = false
                } else {
                    binding.bottomNavigation.menu.getItem(0).isChecked = false
                }
                menuItem = binding.bottomNavigation.menu.getItem(position)
                menuItem!!.isChecked = true
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

    override fun observeRequestState() {

    }

    private fun showAlertDialog() {
        AlertMessageDialog.Builder()
            .setContext(this)
            .setTitle("温馨提醒")
            .setMessage("手机没有安装《钉钉》软件，无法自动打卡")
            .setPositiveButton("知道了")
            .setOnDialogButtonClickListener(object :
                AlertMessageDialog.OnDialogButtonClickListener {
                override fun onConfirmClick() {

                }
            }).build().show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - clickTime > 2000) {
                "再按一次退出应用".show(this)
                clickTime = System.currentTimeMillis()
                true
            } else {
                super.onKeyDown(keyCode, event)
            }
        } else super.onKeyDown(keyCode, event)
    }

    override fun onStart() {
        super.onStart()
        // 注册接收器，监听网络变化
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        // 注销接收器
        unregisterReceiver(networkChangeReceiver)
    }

    fun connectToMqtt() {
        Log.d("MQTT", "Starting MQTT connection")
        // 确保网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Log.e("MQTT", "No network available")
            return
        }

        mqttClient = MqttAndroidClient(applicationContext, mqttServerUrl, mqttClientId, Ack.AUTO_ACK)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
            userName = user // 替换为你的用户名
            password = pwd.toCharArray() // 替换为你的密码
        }

        try {
            Log.d("MQTT", "Connecting to MQTT broker at $mqttServerUrl")
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "MQTT connection successful")
                    "MQTT连接成功".show(this@MainActivity)

                    val topicsToSubscribe = arrayOf(mqttTopic1, mqttTopic2)
                    val qosLevels = intArrayOf(0, 1) // 对应的QoS级别
                    subscribeToTopics(topicsToSubscribe, qosLevels) // 连接成功后订阅主题
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "MQTT connection failed: ${exception?.message}")
                    "MQTT连接失败: ${exception?.message}".show(this@MainActivity)
                }
            })
        } catch (e: MqttException) {
            Log.e("MQTT", "MQTT connection exception: ${e.message}")
            e.printStackTrace()
            "MQTT连接异常: ${e.message}".show(this@MainActivity)
        }
    }

    //mqtt订阅
    private fun subscribeToTopics(topics: Array<String>, qos: IntArray) {
        try {
            mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    "成功订阅主题: ${topics.joinToString(", ")}".show(this@MainActivity)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    "订阅失败: ${exception?.message}".show(this@MainActivity)
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
            "订阅异常: ${e.message}".show(this@MainActivity)
        }
    }

    //mqtt解除订阅
    /**
     * use:
     * private fun someMethodToUnsubscribe() {
     *     val topicsToUnsubscribe = arrayOf("your/topic1", "your/topic2") // 替换为要解除订阅的主题
     *     unsubscribeFromTopics(topicsToUnsubscribe)
     * }
     */
    private fun unsubscribeFromTopics(topics: Array<String>) {
        try {
            mqttClient.unsubscribe(topics, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    "成功解除订阅主题: ${topics.joinToString(", ")}".show(this@MainActivity)
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    "解除订阅失败: ${exception?.message}".show(this@MainActivity)
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
            "解除订阅异常: ${e.message}".show(this@MainActivity)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

}