package com.adobe.marketing.mobile.services.ui.notification.builders

import android.app.Activity
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.widget.RemoteViews
import com.adobe.marketing.mobile.services.AppContextService
import com.adobe.marketing.mobile.services.DataStoring
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.services.caching.CacheService
import com.adobe.marketing.mobile.services.ui.notification.PushTemplateConstants
import com.adobe.marketing.mobile.services.ui.notification.PushTemplateImageUtils
import com.adobe.marketing.mobile.services.ui.notification.extensions.setBundledLargeIcon
import com.adobe.marketing.mobile.services.ui.notification.templates.ManualCarouselPushTemplate
import com.adobe.marketing.mobile.services.ui.notification.testutils.getMockedBundleWithManualCarouselData
import com.adobe.marketing.mobile.services.ui.notification.testutils.getMockedIntent
import com.adobe.marketing.mobile.services.ui.notification.testutils.getMockedMapWithManualCarouselData
import com.adobe.marketing.mobile.util.UrlUtils
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@RunWith(MockitoJUnitRunner.Silent::class)
class ManualCarouselNotificationBuilderTest {

   /* @Test
    fun test_fallback_to_basic_notification_when_no_images_downloaded() {
        val context = mock<Context>()
        val pushTemplate = ManualCarouselPushTemplate(getMockedMapWithManualCarouselData())
        val trackerActivityClass = mock<Activity>()
        val broadcastReceiverClass = mock<BroadcastReceiver>()
        val notificationManager = mock<NotificationManager>()
        `when`(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
        `when`(PushTemplateImageUtils.cacheImages(any())).thenReturn(0)

        val builder = ManualCarouselNotificationBuilder.construct(context, pushTemplate, trackerActivityClass.javaClass, broadcastReceiverClass.javaClass)
        assertNotNull(builder)

    }*/

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockPushTemplate: ManualCarouselPushTemplate

    @Mock
    private lateinit var mockCacheService: CacheService

    @Mock
    private lateinit var mockNotificationManager: NotificationManager

    @Mock
    private lateinit var mockServiceProvider: ServiceProvider

    private lateinit var mockedStaticServiceProvider: MockedStatic<ServiceProvider>

    @Before
    fun setUp() {
        mockedStaticServiceProvider = Mockito.mockStatic(ServiceProvider::class.java)
        mockedStaticServiceProvider.`when`<Any> { ServiceProvider.getInstance() }
            .thenReturn(mockServiceProvider)
        `when`(mockServiceProvider.appContextService).thenReturn(mock<AppContextService>())
        `when`(mockServiceProvider.cacheService).thenReturn(mockCacheService)
        `when`(mockServiceProvider.dataStoreService).thenReturn(mock<DataStoring>())
        `when`(mockServiceProvider.dataStoreService).thenReturn(mock<DataStoring>())
    }

    @Test
    fun `temptest`(){
        val mockIntent = getMockedIntent()
        val mockPackageManager = mock<PackageManager>()
        val mockBundle = getMockedBundleWithManualCarouselData()
        `when`(mockIntent.extras).thenReturn(mockBundle)
        val pushTemplate = ManualCarouselPushTemplate(mockIntent)
        val channelIdToUse = "channelId"
        val trackerActivityClass = mock<Activity>().javaClass
        val smallLayout = mock<RemoteViews>()
        val expandedLayout = mock<RemoteViews>()
        val containerLayoutViewId = 123
        val context = Mockito.mock(Context::class.java)
        val resources = Mockito.mock(Resources::class.java)
        val remoteViews = Mockito.mock(RemoteViews::class.java)
        val mockAppInfo = mock<ApplicationInfo>()
        whenever(context.resources).thenReturn(resources)
        whenever(context.packageName).thenReturn("com.example")
        whenever(context.packageManager).thenReturn(mockPackageManager)

        whenever(mockPackageManager.getApplicationInfo(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(mockAppInfo)
        whenever(resources.getIdentifier("valid_icon", "drawable", "com.example")).thenReturn(123)

        remoteViews.setBundledLargeIcon("valid_icon")

       // `when`(pushTemplate.isFromIntent).thenReturn( true)
        //`when`(pushTemplate.channelId) .thenReturn( "channel_id")

// Act
        val result = AEPPushNotificationBuilder.construct(
            context,
            pushTemplate,
            channelIdToUse,
            trackerActivityClass,
            smallLayout,
            expandedLayout,
            containerLayoutViewId
        )

// Assert
        assertNotNull(result)
    }
}

   /* @Test
    fun `test construct with available cache service`() {
        `when`(mockServiceProvider.cacheService).thenReturn(mockCacheService)

        // Mock the necessary methods and fields of pushTemplate
        `when`(mockPushTemplate.isFromIntent).thenReturn(true)
        `when`(mockPushTemplate.carouselLayoutType).thenReturn(PushTemplateConstants.DefaultValues.FILMSTRIP_CAROUSEL_MODE)
        `when`(mockPushTemplate.channelId).thenReturn("channel_id")
        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mockNotificationManager)

        // Mock AEPPushNotificationBuilder.setupSilentNotificationChannel() for API >= 26
        *//*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        }*//*

        // Mock AEPPushNotificationBuilder.createChannelIfRequired()
        `when`(
            mockNotificationManager.createNotificationChannelIfRequired(
                mockContext,
                mockPushTemplate.channelId,
                "Halo",
                3,
                true
            )
        ).thenReturn("channel_id")

        // Mock AEPPushNotificationBuilder.construct() method
        val notificationBuilder = NotificationCompat.Builder(mockContext)
        `when`(
            AEPPushNotificationBuilder.construct(
                mockContext,
                mock(ManualCarouselPushTemplate::class.java),
                "channel_id",
                mock(Activity::class.java).javaClass,
                RemoteViews(mockContext.packageName, anyInt()),
                RemoteViews(mockContext.packageName, anyInt()),
                anyInt()
            )
        ).thenReturn(notificationBuilder)

        // Call the construct method and verify the result
        val result = ManualCarouselNotificationBuilder.construct(
            mockContext,
            mockPushTemplate,
            mock(Activity::class.java).javaClass,
            mock(BroadcastReceiver::class.java).javaClass
        )

        assertNotNull(result)
        assertEquals(notificationBuilder, result)
    }

    @Test(expected = NotificationConstructionFailedException::class)
    fun `test construct with unavailable cache service`() {
        // Mock ServiceProvider to return null, simulating an unavailable cache service
        `when`(mockServiceProvider.cacheService).thenReturn(null)

        // Call the construct method and expect NotificationConstructionFailedException
        ManualCarouselNotificationBuilder.construct(
            mockContext,
            mockPushTemplate,
            mock(Activity::class.java).javaClass,
            mock(BroadcastReceiver::class.java).javaClass
        )
    }

    @After
    fun teardown() {
        mockedStaticServiceProvider.close()
    }*/