package org.smssecure.smssecure.util.dualsim;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import org.smssecure.smssecure.crypto.IdentityKeyUtil;
import org.smssecure.smssecure.crypto.MasterSecret;
import org.smssecure.smssecure.crypto.MasterSecretUtil;
import org.smssecure.smssecure.crypto.storage.SilenceSessionStore;
import org.smssecure.smssecure.util.SilencePreferences;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class DualSimUpgradeUtil {
  private static final String TAG = DualSimUpgradeUtil.class.getSimpleName();

  public static void moveIdentityKeysAndSessionsToSubscriptionId(Context context, int originalSubscriptionId, int subscriptionId) {
    Log.w(TAG, "moveIdentityKeysMasterSecretAndSessionsToSubscriptionId(" + originalSubscriptionId + ", " + subscriptionId + ")");

    moveIdentityKeysToSubscriptionId(context, originalSubscriptionId, subscriptionId);
    moveSessionsToSubscriptionId(context, originalSubscriptionId, subscriptionId);
  }

  private static void moveIdentityKeysToSubscriptionId(Context context, int originalSubscriptionId, int subscriptionId) {
    String originalIdentityPublicPref  = IdentityKeyUtil.getIdentityPublicKeyDjbPref(originalSubscriptionId);
    String identityPublicPref          = IdentityKeyUtil.getIdentityPublicKeyDjbPref(subscriptionId);
    String originalIdentityPrivatePref = IdentityKeyUtil.getIdentityPrivateKeyDjbPref(originalSubscriptionId);
    String identityPrivatePref         = IdentityKeyUtil.getIdentityPrivateKeyDjbPref(subscriptionId);

    Log.w(TAG, "Moving " + originalIdentityPublicPref  + " to " + identityPublicPref);
    Log.w(TAG, "Moving " + originalIdentityPrivatePref + " to " + identityPrivatePref);

    String identityPublicKey  = IdentityKeyUtil.retrieve(context, originalIdentityPublicPref);
    String identityPrivateKey = IdentityKeyUtil.retrieve(context, originalIdentityPrivatePref);

    IdentityKeyUtil.save(context, identityPublicPref, identityPublicKey);
    IdentityKeyUtil.save(context, identityPrivatePref, identityPrivateKey);

    IdentityKeyUtil.remove(context, originalIdentityPublicPref);
    IdentityKeyUtil.remove(context, originalIdentityPrivatePref);
  }

  private static void moveSessionsToSubscriptionId(Context context, int originalSubscriptionId, int subscriptionId) {
    File sessionDirectory = SilenceSessionStore.getSessionDirectory(context);

    File[] sessionList = sessionDirectory.listFiles();

    String destinationSuffix = subscriptionId != -1 ? "." + subscriptionId : "";

    for (File session : sessionList){
      if (session.isFile()){
        String absolutePath = session.getAbsolutePath();
        String newSessionName = null;

        if (originalSubscriptionId != -1 && absolutePath.endsWith("." + originalSubscriptionId)) {
          newSessionName = absolutePath.replaceAll("/\\." + originalSubscriptionId + "/g", destinationSuffix);
        } else if (originalSubscriptionId == -1) {
          newSessionName = absolutePath + destinationSuffix;
        }

        if (newSessionName != null) {
          Log.w(TAG, "Moving session " + absolutePath + " to " + newSessionName);
          File newFile = new File(newSessionName);
          if (session.renameTo(newFile)) {
            Log.w(TAG, "Done!");
          } else {
            Log.w(TAG, "Failed!");
          }
        }

      }
    }
  }

  public static void generateKeysIfDoNotExist(Context context, MasterSecret masterSecret, List<SubscriptionInfoCompat> activeSubscriptions) {
    for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
      int subscriptionId = subscriptionInfo.getSubscriptionId();

      if (!IdentityKeyUtil.hasIdentityKey(context, subscriptionId))
        IdentityKeyUtil.generateIdentityKeys(context, masterSecret, subscriptionId);
    }
  }

  public static void bindSubscriptionId(Context context, List<SubscriptionInfoCompat> activeSubscriptions) {
    List<SubscriptionInfoCompat> remainingSubscriptions;
    remainingSubscriptions = bindSubscriptionIdToPhoneNumber(context, activeSubscriptions);
    remainingSubscriptions = bindSubscriptionIdToIccId(context, remainingSubscriptions);

    if (remainingSubscriptions.size() > 0) Log.w(TAG, "WARNING: Cannot bind " + remainingSubscriptions.size() + " subscription(s)!");
  }

  private static List<SubscriptionInfoCompat> bindSubscriptionIdToPhoneNumber(Context context, List<SubscriptionInfoCompat> activeSubscriptions) {
    List<SubscriptionInfoCompat> remainingSubscriptions = new LinkedList<SubscriptionInfoCompat>(activeSubscriptions);

    for (SubscriptionInfoCompat subscriptionInfo : new LinkedList<SubscriptionInfoCompat>(activeSubscriptions)) {
      String number = subscriptionInfo.getNumber();
      if (number != null && !number.equals("")) {
        SilencePreferences.setNumberForSubscriptionId(context, subscriptionInfo.getSubscriptionId(), number);
        remainingSubscriptions.remove(subscriptionInfo);
      }
    }

    return remainingSubscriptions;
  }

  private static List<SubscriptionInfoCompat> bindSubscriptionIdToIccId(Context context, List<SubscriptionInfoCompat> activeSubscriptions) {
    List<SubscriptionInfoCompat> remainingSubscriptions = new LinkedList<SubscriptionInfoCompat>(activeSubscriptions);

    for (SubscriptionInfoCompat subscriptionInfo : new LinkedList<SubscriptionInfoCompat>(activeSubscriptions)) {
      String iccId = subscriptionInfo.getIccId();
      if (iccId != null && !iccId.equals("")) {
        SilencePreferences.setIccIdForSubscriptionId(context, subscriptionInfo.getSubscriptionId(), iccId);
        remainingSubscriptions.remove(subscriptionInfo);
      }
    }

    return remainingSubscriptions;
  }

  public static void bindAppSubscriptionId(Context context, List<SubscriptionInfoCompat> activeSubscriptions) {
    if (Build.VERSION.SDK_INT >= 22) {
      for (SubscriptionInfoCompat subscriptionInfo : activeSubscriptions) {
        int appSubscriptionId = SilencePreferences.getLastAppSubscriptionId(context) + 1;
        if (subscriptionInfo.getSubscriptionId() == -1) SilencePreferences.setAppSubscriptionId(context, subscriptionInfo.getDeviceSubscriptionId(), appSubscriptionId);
      }
    }
  }
}
