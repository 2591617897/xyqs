import pymysql
try:
    conn = pymysql.connect(host='localhost', user='root', password='123456', database='guizhou_campus_fun', charset='utf8mb4', connect_timeout=3)
    cur = conn.cursor()
    cur.execute('SELECT 1')
    print(f'MySQL OK: {cur.fetchone()}')
    cur.execute("SHOW TABLES LIKE 't_notification'")
    print(f't_notification exists: {cur.fetchone()}')
    cur.execute("SHOW TABLES LIKE 't_message_conversation'")
    print(f't_message_conversation exists: {cur.fetchone()}')
    cur.execute("SHOW TABLES LIKE 't_message_conversation_hide'")
    print(f't_message_conversation_hide exists: {cur.fetchone()}')
    conn.close()
except Exception as e:
    print(f'FAIL: {e}')
