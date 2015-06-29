package org.smssecure.smssecure.components;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import org.smssecure.smssecure.R;
import org.smssecure.smssecure.contacts.avatars.ContactColors;
import org.smssecure.smssecure.contacts.avatars.ContactPhotoFactory;
import org.smssecure.smssecure.recipients.Recipient;
import org.smssecure.smssecure.recipients.RecipientFactory;
import org.smssecure.smssecure.recipients.Recipients;

public class AvatarImageView extends ImageView {

  private boolean inverted;

  public AvatarImageView(Context context) {
    super(context);
    setScaleType(ScaleType.CENTER_CROP);
  }

  public AvatarImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setScaleType(ScaleType.CENTER_CROP);

    if (attrs != null) {
      TypedArray typedArray = context.getTheme().obtainStyledAttributes(attrs, R.styleable.AvatarImageView, 0, 0);
      inverted = typedArray.getBoolean(0, false);
      typedArray.recycle();
    }
  }

  public void setAvatar(@Nullable Recipients recipients, boolean quickContactEnabled) {
    if (recipients != null) {
      int backgroundColor = recipients.getColor().or(ContactColors.UNKNOWN_COLOR);
      setImageDrawable(recipients.getContactPhoto().asDrawable(getContext(), backgroundColor, inverted));
      setAvatarClickHandler(recipients, quickContactEnabled);
    } else {
      setImageDrawable(ContactPhotoFactory.getDefaultContactPhoto(null).asDrawable(getContext(), ContactColors.UNKNOWN_COLOR, inverted));
      setOnClickListener(null);
    }
  }

  public void setAvatar(@Nullable Recipient recipient, boolean quickContactEnabled) {
    setAvatar(RecipientFactory.getRecipientsFor(getContext(), recipient, true), quickContactEnabled);
  }

  private void setAvatarClickHandler(final Recipients recipients, boolean quickContactEnabled) {
    if (!recipients.isGroupRecipient() && quickContactEnabled) {
      setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          Recipient recipient = recipients.getPrimaryRecipient();

          if (recipient != null && recipient.getContactUri() != null) {
            ContactsContract.QuickContact.showQuickContact(getContext(), AvatarImageView.this, recipient.getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
          } else if (recipient != null) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, recipient.getNumber());
            intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            getContext().startActivity(intent);
          }
        }
      });
    } else {
      setOnClickListener(null);
    }
  }

}
