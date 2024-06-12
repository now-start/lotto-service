#!/bin/sh
python /app/src/module/Email.py
echo "$LOTTO_SCHEDULE python /app/src/LottoServiceApplication.py >> /var/log/cron.log 2>&1" | crontab -
cron -f