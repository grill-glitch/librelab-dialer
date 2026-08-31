#!/system/bin/sh
# verify_lookup.sh — Verify PhoneLookup API and contact provider are accessible on device
# Run: adb shell sh /sdcard/verify_lookup.sh

LOG=/sdcard/lookup_verify.log
echo "=== PhoneLookup API Verification ===" > $LOG
echo "Time: $(date)" >> $LOG

# 1. Check READ_CONTACTS permission
echo "" >> $LOG
echo "[1] READ_CONTACTS permission:" >> $LOG
dumpsys package org.librelab.dialer | grep "READ_CONTACTS" | head -3 >> $LOG 2>&1

# 2. Try a content query on PhoneLookup (anonymous number that exists in most phones)
echo "" >> $LOG
echo "[2] PhoneLookup query test:" >> $LOG
content query \
    --uri content://com.android.contacts/phone_lookup/100 \
    --projection display_name:0,name:1,type:2,label:3 \
    >> $LOG 2>&1
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "PhoneLookup query: SUCCESS" >> $LOG
else
    echo "PhoneLookup query: FAILED (exit $RESULT)" >> $LOG
fi

# 3. Count contacts
echo "" >> $LOG
echo "[3] Total contacts count:" >> $LOG
content query \
    --uri content://com.android.contacts/contacts \
    --projection _id \
    >> $LOG 2>&1
echo "Contact count query done (check above for cursor output)" >> $LOG

# 4. Check ContactsProvider availability
echo "" >> $LOG
echo "[4] ContactsProvider package:" >> $LOG
dumpsys package com.android.providers.contacts | grep "versionName" | head -1 >> $LOG 2>&1

# 5. Test PhoneLookup with known test number (empty result is OK, crash = broken)
echo "" >> $LOG
echo "[5] PhoneLookup with non-existent number (should return empty, not crash):" >> $LOG
content query \
    --uri content://com.android.contacts/phone_lookup/99999999999 \
    >> $LOG 2>&1
echo "Non-crash test complete (exit $?)" >> $LOG

echo "" >> $LOG
echo "=== Done ===" >> $LOG
echo "Log: $LOG"
cat $LOG
