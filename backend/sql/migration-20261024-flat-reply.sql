-- 楼中楼拍平修复（2026-10-24）
-- 背景：楼中楼只有两层（顶层评论 + 回复），组树按 parent_id=顶层id 分组。
-- 历史上回复「子回复」时 parentId 直接挂子回复 id，导致孙回复组树时永远查不到（发了不显示）。
-- 后端 addComment 已改为：目标本身是子回复时拍平挂到其顶层评论下。
-- 本脚本把存量脏数据重锚到顶层评论 id，被回复人快照 reply_to_name 已记目标本人，无需改动。
USE guizhou_campus_fun;

UPDATE t_wall_comment c
JOIN t_wall_comment p ON p.id = c.parent_id AND p.parent_id > 0
SET c.parent_id = p.parent_id
WHERE c.parent_id > 0;

UPDATE t_post_comment c
JOIN t_post_comment p ON p.id = c.parent_id AND p.parent_id > 0
SET c.parent_id = p.parent_id
WHERE c.parent_id > 0;
