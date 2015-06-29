/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure.recipients;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.smssecure.smssecure.contacts.avatars.ContactPhoto;
import org.smssecure.smssecure.contacts.avatars.ContactPhotoFactory;
import org.smssecure.smssecure.database.CanonicalAddressDatabase;
import org.smssecure.smssecure.database.DatabaseFactory;
import org.smssecure.smssecure.database.GroupDatabase;
import org.smssecure.smssecure.database.RecipientPreferenceDatabase.RecipientsPreferences;
import org.smssecure.smssecure.util.GroupUtil;
import org.smssecure.smssecure.util.LRUCache;
import org.smssecure.smssecure.util.ListenableFutureTask;
import org.smssecure.smssecure.util.Util;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class RecipientProvider {

  private static final String TAG = RecipientProvider.class.getSimpleName();

  private static final Map<Long,Recipient>          recipientCache         = Collections.synchronizedMap(new LRUCache<Long,Recipient>(1000));
  private static final Map<RecipientIds,Recipients> recipientsCache        = Collections.synchronizedMap(new LRUCache<RecipientIds, Recipients>(1000));
  private static final ExecutorService              asyncRecipientResolver = Util.newSingleThreadedLifoExecutor();

  private static final String[] CALLER_ID_PROJECTION = new String[] {
    PhoneLookup.DISPLAY_NAME,
    PhoneLookup.LOOKUP_KEY,
    PhoneLookup._ID,
    PhoneLookup.NUMBER
  };

  Recipient getRecipient(Context context, long recipientId, boolean asynchronous) {
    Recipient cachedRecipient = recipientCache.get(recipientId);
    if (cachedRecipient != null) return cachedRecipient;

    String number = CanonicalAddressDatabase.getInstance(context).getAddressFromId(recipientId);

    if (asynchronous) {
      cachedRecipient = new Recipient(recipientId, number, getRecipientDetailsAsync(context, recipientId, number));
    } else {
      cachedRecipient = new Recipient(recipientId, getRecipientDetailsSync(context, recipientId, number));
    }

    recipientCache.put(recipientId, cachedRecipient);
    return cachedRecipient;
  }

  Recipients getRecipients(Context context, long[] recipientIds, boolean asynchronous) {
    Recipients cachedRecipients = recipientsCache.get(new RecipientIds(recipientIds));
    if (cachedRecipients != null) return cachedRecipients;

    List<Recipient> recipientList = new LinkedList<>();

    for (long recipientId : recipientIds) {
      recipientList.add(getRecipient(context, recipientId, asynchronous));
    }

    if (asynchronous) cachedRecipients = new Recipients(recipientList, getRecipientsPreferencesAsync(context, recipientIds));
    else              cachedRecipients = new Recipients(recipientList, getRecipientsPreferencesSync(context, recipientIds));

    recipientsCache.put(new RecipientIds(recipientIds), cachedRecipients);
    return cachedRecipients;
  }

  void clearCache() {
    recipientCache.clear();
    recipientsCache.clear();
  }

  private @NonNull ListenableFutureTask<RecipientDetails> getRecipientDetailsAsync(final Context context,
                                                                                   final long recipientId,
                                                                                   final String number)
  {
    Callable<RecipientDetails> task = new Callable<RecipientDetails>() {
      @Override
      public RecipientDetails call() throws Exception {
        return getRecipientDetailsSync(context, recipientId, number);
      }
    };

    ListenableFutureTask<RecipientDetails> future = new ListenableFutureTask<>(task);
    asyncRecipientResolver.submit(future);
    return future;
  }

  private @NonNull RecipientDetails getRecipientDetailsSync(Context context, long recipientId, String number) {
    if (GroupUtil.isEncodedGroup(number)) return getGroupRecipientDetails(context, number);
    else                                  return getIndividualRecipientDetails(context, recipientId, number);
  }

  private @NonNull RecipientDetails getIndividualRecipientDetails(Context context, long recipientId, String number) {
    Optional<RecipientsPreferences> preferences = DatabaseFactory.getRecipientPreferenceDatabase(context).getRecipientsPreferences(new long[]{recipientId});
    Optional<Integer>               color       = preferences.isPresent() ? preferences.get().getColor() : Optional.<Integer>absent();
    Uri                             uri         = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
    Cursor                          cursor      = context.getContentResolver().query(uri, CALLER_ID_PROJECTION,
                                                                                     null, null, null);

    try {
      if (cursor != null && cursor.moveToFirst()) {
        Uri          contactUri   = Contacts.getLookupUri(cursor.getLong(2), cursor.getString(1));
        String       name         = cursor.getString(3).equals(cursor.getString(0)) ? null : cursor.getString(0);
        ContactPhoto contactPhoto = ContactPhotoFactory.getContactPhoto(context,
                                                                        Uri.withAppendedPath(Contacts.CONTENT_URI, cursor.getLong(2) + ""),
                                                                        name);

        return new RecipientDetails(cursor.getString(0), cursor.getString(3), contactUri, contactPhoto, color);
      }
    } finally {
      if (cursor != null)
        cursor.close();
    }

    return new RecipientDetails(null, number, null, ContactPhotoFactory.getDefaultContactPhoto(null), color);
  }

  private @NonNull RecipientDetails getGroupRecipientDetails(Context context, String groupId) {
    try {
      GroupDatabase.GroupRecord record  = DatabaseFactory.getGroupDatabase(context)
                                                         .getGroup(GroupUtil.getDecodedId(groupId));

      if (record != null) {
        ContactPhoto contactPhoto = ContactPhotoFactory.getGroupContactPhoto(record.getAvatar());
        return new RecipientDetails(record.getTitle(), groupId, null, contactPhoto, Optional.<Integer>absent());
      }

      return new RecipientDetails(null, groupId, null, ContactPhotoFactory.getDefaultGroupPhoto(), Optional.<Integer>absent());
    } catch (IOException e) {
      Log.w("RecipientProvider", e);
      return new RecipientDetails(null, groupId, null, ContactPhotoFactory.getDefaultGroupPhoto(), Optional.<Integer>absent());
    }
  }

  private @Nullable RecipientsPreferences getRecipientsPreferencesSync(Context context, long[] recipientIds) {
    return DatabaseFactory.getRecipientPreferenceDatabase(context)
                          .getRecipientsPreferences(recipientIds)
                          .orNull();
  }

  private ListenableFutureTask<RecipientsPreferences> getRecipientsPreferencesAsync(final Context context, final long[] recipientIds) {
    ListenableFutureTask<RecipientsPreferences> task = new ListenableFutureTask<>(new Callable<RecipientsPreferences>() {
      @Override
      public RecipientsPreferences call() throws Exception {
        return getRecipientsPreferencesSync(context, recipientIds);
      }
    });

    asyncRecipientResolver.execute(task);

    return task;
  }

  public static class RecipientDetails {
    @Nullable public final String            name;
    @NonNull  public final String            number;
    @NonNull  public final ContactPhoto      avatar;
    @Nullable public final Uri               contactUri;
    @NonNull  public final Optional<Integer> color;

    public RecipientDetails(@Nullable String name, @NonNull String number,
                            @Nullable Uri contactUri, @NonNull ContactPhoto avatar,
                            @NonNull  Optional<Integer> color)
    {
      this.name          = name;
      this.number        = number;
      this.avatar        = avatar;
      this.contactUri    = contactUri;
      this.color         = color;
    }
  }

  private static class RecipientIds {
    private final long[] ids;

    private RecipientIds(long[] ids) {
      this.ids = ids;
    }

    public boolean equals(Object other) {
      if (other == null || !(other instanceof RecipientIds)) return false;
      return Arrays.equals(this.ids, ((RecipientIds) other).ids);
    }

    public int hashCode() {
      return Arrays.hashCode(ids);
    }
  }



}