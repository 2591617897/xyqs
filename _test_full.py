import requests, pymysql, json, time

BASE = "http://localhost:8888/api"

la = requests.post(f"{BASE}/auth/login", json={"code":"dev_openid_guizhou_campus_001","nickname":"林小满"}, timeout=8).json()
ta = la["data"]["token"]; uidA = int(la["data"]["user"]["id"])  # int 化方便 DB 查询
lb = requests.post(f"{BASE}/auth/login", json={"code":"demo_openid_006","nickname":"未名风"}, timeout=8).json()
tb = lb["data"]["token"]; uidB = int(lb["data"]["user"]["id"])

hA = {"Authorization": f"Bearer {ta}"}
hB = {"Authorization": f"Bearer {tb}"}

# 清空通知表
conn = pymysql.connect(host="localhost", user="root", password="123456", database="guizhou_campus_fun", charset="utf8mb4")
conn.cursor().execute("DELETE FROM t_notification"); conn.commit(); conn.close()
print(f"DB cleared. uidA={uidA} uidB={uidB}")

# A 发布一个新商品（作为内容作者）
print("\n1️⃣ A publishes new goods (will be content author)")
goods = requests.post(f"{BASE}/goods", json={
    "title": "A 的测试宝贝",
    "price": 99,
    "description": "用于 reply-to 通知测试",
    "images": ["https://via.placeholder.com/200"],
    "category": "数码",
    "condition": "9成新"
}, headers=hA, timeout=8).json()
print(f"   result: {goods}")
gid = goods.get("data", {}).get("id")
if not gid:
    print("FAIL: A cannot publish goods"); exit()
print(f"   new goods id={gid} (owner=A uidA={uidA})")

# B 点赞 A 的商品 → LIKE_GOODS 通知 A
print(f"\n2️⃣ B like A's goods → LIKE_GOODS to A")
r = requests.post(f"{BASE}/goods/{gid}/like", headers=hB, timeout=8).json()
print(f"   {r}")

# B 评论 A 的商品 → COMMENT_GOODS 通知 A
print(f"\n3️⃣ B comment A's goods → COMMENT_GOODS to A")
r = requests.post(f"{BASE}/goods/{gid}/comments",
    json={"content": "哇好便宜！我要啦~"},
    headers=hB, timeout=8).json()
print(f"   {r}")
bCommentId = r.get("data")
print(f"   B's comment id={bCommentId}")

# A 回复 B 的评论 → reply-to COMMENT_GOODS 通知 B
print(f"\n4️⃣ A reply B's comment(id={bCommentId}) → reply-to COMMENT_GOODS to B")
r = requests.post(f"{BASE}/goods/{gid}/comments",
    json={"content": "没问题！明天见~", "parentId": bCommentId},
    headers=hA, timeout=8).json()
print(f"   {r}")

# B 关注 A → NEW_FOLLOWER 通知 A
print(f"\n5️⃣ B follow A → NEW_FOLLOWER to A")
r = requests.post(f"{BASE}/user/follow/{uidA}", headers=hB, timeout=8).json()
print(f"   {r}")

time.sleep(2)  # 等 CompletableFuture.runAsync + Feign + DB insert

# ==== 验证 ====
print("\n" + "="*60)
print("===== DB VERIFICATION =====")
conn = pymysql.connect(host="localhost", user="root", password="123456", database="guizhou_campus_fun", charset="utf8mb4")
cur = conn.cursor()
cur.execute("""
    SELECT type, receiver_id, actor_id, target_type, target_id, content_snapshot
    FROM t_notification ORDER BY created_at DESC
""")
for row in cur.fetchall():
    print(f"  [{row[0]}] receiver={row[1]} actor={row[2]} target={row[3]}:{row[4]} snp={row[5][:20] if row[5] else 'None'}")

print("\n===== EXPECTATION CHECK =====")
cur.execute("SELECT COUNT(*) FROM t_notification WHERE type='LIKE_GOODS' AND receiver_id=%s AND actor_id=%s", (uidA, uidB))
likeCount = cur.fetchone()[0]
print(f"✅ LIKE_GOODS B→A: {likeCount} (expect 1)")

cur.execute("SELECT COUNT(*) FROM t_notification WHERE type='COMMENT_GOODS' AND receiver_id=%s AND actor_id=%s", (uidA, uidB))
commentCount = cur.fetchone()[0]
print(f"✅ COMMENT_GOODS B→A: {commentCount} (expect 1: B's comment to A's goods)")

cur.execute("SELECT COUNT(*) FROM t_notification WHERE type='COMMENT_GOODS' AND receiver_id=%s AND actor_id=%s", (uidB, uidA))
replyCount = cur.fetchone()[0]
print(f"✅ COMMENT_GOODS A→B: {replyCount} (expect 1: A's reply-to B's comment — NEW FEATURE!)")

cur.execute("SELECT COUNT(*) FROM t_notification WHERE type='NEW_FOLLOWER' AND receiver_id=%s AND actor_id=%s", (uidA, uidB))
followCount = cur.fetchone()[0]
print(f"✅ NEW_FOLLOWER B→A: {followCount} (expect 1)")

allPass = (likeCount >= 1 and commentCount >= 1 and replyCount >= 1 and followCount >= 1)
conn.close()

# API 验证 actorName enrich
print("\n===== API actorName enrich =====")
r = requests.get(f"{BASE}/messages/notifications?size=20", headers=hA, timeout=8).json()
for n in r.get("data", {}).get("records", []):
    print(f"  [{n['type']}] actorName={n.get('actorName','???')}")

print("\n" + "="*60)
if allPass:
    print("🎉🎉🎉 ALL 4 SCENARIOS PASSED! 🎉🎉🎉")
else:
    print("⚠️  SOME SCENARIOS FAILED, CHECK ABOVE")
