package org.smssecure.smssecure.util.dualsim;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.smssecure.smssecure.util.SilencePreferences;

public class SubscriptionInfoCompat {

  private final int                    deviceSubscriptionId;
  private final int                    subscriptionId;
  private final @Nullable CharSequence displayName;
  private final @Nullable String       number;
  private final @Nullable String       iccId;

  public SubscriptionInfoCompat(Context      context,
                                int          deviceSubscriptionId,
                      @Nullable CharSequence displayName,
                      @Nullable String       number,
                      @Nullable String       iccId)
  {
    int subscriptionId = SilencePreferences.getAppSubscriptionId(context, deviceSubscriptionId);

    if (Build.VERSION.SDK_INT >= 22 && subscriptionId == -1) {
      subscriptionId = SilencePreferences.getLastAppSubscriptionId(context) + 1;
      SilencePreferences.setAppSubscriptionId(context, deviceSubscriptionId, subscriptionId);
    }

    this.deviceSubscriptionId = deviceSubscriptionId;
    this.subscriptionId       = subscriptionId;
    this.displayName          = displayName;
    this.number               = number;
    this.iccId                = iccId;
  }

  public @NonNull CharSequence getDisplayName() {
    return displayName != null ? displayName : "";
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public int getDeviceSubscriptionId() {
    return deviceSubscriptionId;
  }

  public String getNumber() {
    return number != null ? number : "";
  }

  public String getIccId() {
    return iccId;
  }
}
