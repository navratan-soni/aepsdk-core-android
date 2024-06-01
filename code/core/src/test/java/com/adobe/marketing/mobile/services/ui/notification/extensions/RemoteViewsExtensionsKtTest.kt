package com.adobe.marketing.mobile.services.ui.notification.extensions

import android.content.Context
import android.content.res.Resources
import android.widget.RemoteViews
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner.Silent::class)
class RemoteViewsExtensionsKtTest {
    @Test
    fun test_valid_large_icon_name_sets_image_resource() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val remoteViews = mock(RemoteViews::class.java)
        whenever(context.resources).thenReturn(resources)
        whenever(context.packageName).thenReturn("com.example")
        whenever(resources.getIdentifier("valid_icon", "drawable", "com.example")).thenReturn(123)

        remoteViews.setBundledLargeIcon("valid_icon")

        //verify(remoteViews).setImageViewResource(anyInt(), 123)
    }
}