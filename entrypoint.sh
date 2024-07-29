#!/bin/sh
python /app/src/module/Email.py
(
  env
  echo "$LOTTO_SCHEDULE python /app/src/LottoServiceApplication.py >> /var/log/cron.log 2>&1"
) | crontab -
cron -f