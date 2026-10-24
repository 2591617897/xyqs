import requests, pymysql

BASE = "http://localhost:8888/api"

# 登录 A（林小满 9001）
la = requests.post(f"{BASE}/auth/login", json={"code":"dev_openid_guizhou_campus_001","nickname":"林小满"}, timeout=8).json()
ta = la["data"]["token"]; uidA = la["data"]["user"]["id"]
print(f"A 林小满 id={uidA}")

# 登录 B（未名风 9006）
lb = requests.post(f"{BASE}/auth/login", json={"code":"demo_openid_006","nickname":"未名风"}, timeout=8).json()
tb = lb["data"]["token"]; uidB = lb["data"]["user"]["id"]
print(f"B 未名风 id={uidB}")

hA = {"Authorization": f"Bearer {ta}"}
hB = {"Authorization": f"Bearer {tb}"}

# B 关注 A（应该触发 NEW_FOLLOWER 通知）
r1 = requests.post(f"{BASE}/user/follow/{uidA}", headers=hB, timeout=8).json()
print(f"B follow A: {r1}")

# A 的未读统计
r2 = requests.get(f"{BASE}/messages/notifications/unread", headers=hA, timeout=8).json()
print(f"A unread: {r2}")

# A 的通知列表
r3 = requests.get(f"{BASE}/messages/notifications", headers=hA, timeout=8).json()
print(f"A list: {r3}")

# B 点赞 A 的商品（8001）
r4 = requests.post(f"{BASE}/goods/8001/like", headers=hB, timeout=8).json()
print(f"B like goods 8001: {r4}")

# A 未读再次查
r5 = requests.get(f"{BASE}/messages/notifications/unread", headers=hA, timeout=8).json()
print(f"A unread (after like): {r5}")

# B 点赞 A 的表白墙帖子
r6 = requests.post(f"{BASE}/wall/posts/7001/like", headers=hB, timeout=8).json()
print(f"B like wall 7001: {r6}")

# A 未读再次查
r7 = requests.get(f"{BASE}/messages/notifications/unread", headers=hA, timeout=8).json()
print(f"A unread (after wall like): {r7}")

# DB 查通知
conn = pymysql.connect(host="localhost", user="root", password="123456", database="guizhou_campus_fun", charset="utf8mb4")
cur = conn.cursor()
cur.execute("SELECT id, type, receiver_id, actor_id, target_type, target_id, is_read FROM t_notification ORDER BY created_at DESC LIMIT 15")
print("--- DB notifications ---")
for r in cur.fetchall():
    print(f"  {r}")
conn.close()
