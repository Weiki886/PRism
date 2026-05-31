package com.weiki.prismbackend.service;

import com.weiki.prismbackend.model.dto.RepoBrowsingDTO;
import com.weiki.prismbackend.model.dto.RepoInfo;

import java.util.List;

public interface RepoBrowsingService {

    /**
     * 记录一次仓库浏览：若 (userId, repoUrl) 已存在则更新展示字段并刷新 lastVisitedAt，否则插入新记录。
     */
    void recordVisit(Long userId, String repoUrl, RepoInfo info);

    /**
     * 返回当前用户最近浏览过的仓库，按 lastVisitedAt 倒序，最多 limit 条。
     */
    List<RepoBrowsingDTO> listRecent(Long userId, int limit);

    /**
     * 删除当前用户的一条浏览记录，记录不属于该用户时按未找到处理。
     */
    void deleteById(Long userId, Long id);

    /**
     * 清空当前用户的所有浏览记录（逻辑删除）。
     */
    void clearAll(Long userId);
}
