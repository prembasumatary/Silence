package org.smssecure.smssecure.util.dualsim;

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import org.smssecure.smssecure.util.ServiceUtil;

import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;

public class SubscriptionManagerCompat {

  private final Context context;

  public SubscriptionManagerCompat(Context context) {
    this.context = context.getApplicationContext();
  }

  public Optional<SubscriptionInfoCompat> getActiveSubscriptionInfo(int subscriptionId) {
    if (getActiveSubscriptionInfoList().size() <= 0) {
      return Optional.absent();
    }

    SubscriptionInfo subscriptionInfo = SubscriptionManager.from(context).getActiveSubscriptionInfo(subscriptionId);

    if (subscriptionInfo != null) {
      return Optional.of(new SubscriptionInfoCompat(context,
                                                    subscriptionId,
                                                    subscriptionInfo.getDisplayName(),
                                                    subscriptionInfo.getNumber(),
                                                    subscriptionInfo.getIccId()));
    } else {
      return Optional.absent();
    }
  }

  public @NonNull List<SubscriptionInfoCompat> getActiveSubscriptionInfoList() {
    List<SubscriptionInfoCompat> compatList = new LinkedList<>();

    if (Build.VERSION.SDK_INT < 22) {
      TelephonyManager telephonyManager = ServiceUtil.getTelephonyManager(context);
      compatList.add(new SubscriptionInfoCompat(context,
                                                -1,
                                                telephonyManager.getSimOperatorName(),
                                                telephonyManager.getLine1Number(),
                                                telephonyManager.getSimSerialNumber()));
      return compatList;
    }

    List<SubscriptionInfo> subscriptionInfos = SubscriptionManager.from(context).getActiveSubscriptionInfoList();

    if (subscriptionInfos == null || subscriptionInfos.isEmpty()) {
      return compatList;
    }

    for (SubscriptionInfo subscriptionInfo : subscriptionInfos) {
      compatList.add(new SubscriptionInfoCompat(context,
                                                subscriptionInfo.getSubscriptionId(),
                                                subscriptionInfo.getDisplayName(),
                                                subscriptionInfo.getNumber(),
                                                subscriptionInfo.getIccId()));
    }

    return compatList;
  }

  public static Optional<Integer> getDefaultMessagingSubscriptionId() {
    if (Build.VERSION.SDK_INT < 22) {
      return Optional.absent();
    }
    if(SmsManager.getDefaultSmsSubscriptionId() < 0) {
      return Optional.absent();
    }

    return Optional.of(SmsManager.getDefaultSmsSubscriptionId());
  }

}
