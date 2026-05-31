package com.weiki.prismbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.weiki.prismbackend.common.ResultCode;
import com.weiki.prismbackend.exception.BusinessException;
import com.weiki.prismbackend.mapper.RepoBrowsingMapper;
import com.weiki.prismbackend.model.dto.RepoBrowsingDTO;
import com.weiki.prismbackend.model.dto.RepoInfo;
import com.weiki.prismbackend.model.entity.RepoBrowsing;
import com.weiki.prismbackend.service.RepoBrowsingService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RepoBrowsingServiceImpl implements RepoBrowsingService {

    private static final int MAX_LIMIT = 50;

    private final RepoBrowsingMapper repoBrowsingMapper;

    public RepoBrowsingServiceImpl(RepoBrowsingMapper repoBrowsingMapper) {
        this.repoBrowsingMapper = repoBrowsingMapper;
    }

    @Override
    public void recordVisit(Long userId, String repoUrl, RepoInfo info) {
        if (userId == null || repoUrl == null || info == null) {
            return;
        }
        String normalized = normalize(repoUrl);

        RepoBrowsing existing = repoBrowsingMapper.selectOne(
                new LambdaQueryWrapper<RepoBrowsing>()
                        .eq(RepoBrowsing::getUserId, userId)
                        .eq(RepoBrowsing::getRepoUrl, normalized));

        if (existing != null) {
            existing.setFullName(info.getFullName());
            existing.setDescription(nullSafe(info.getDescription()));
            existing.setLanguage(nullSafe(info.getLanguage()));
            existing.setStarCount(info.getStarCount());
            existing.setOwnerAvatarUrl(nullSafe(info.getOwnerAvatarUrl()));
            existing.setHtmlUrl(nullSafe(info.getHtmlUrl()));
            existing.setIsPrivate(info.isPrivate());
            existing.setLastVisitedAt(LocalDateTime.now());
            repoBrowsingMapper.updateById(existing);
            return;
        }

        RepoBrowsing rb = new RepoBrowsing();
        rb.setUserId(userId);
        rb.setRepoUrl(normalized);
        rb.setFullName(info.getFullName());
        rb.setDescription(nullSafe(info.getDescription()));
        rb.setLanguage(nullSafe(info.getLanguage()));
        rb.setStarCount(info.getStarCount());
        rb.setOwnerAvatarUrl(nullSafe(info.getOwnerAvatarUrl()));
        rb.setHtmlUrl(nullSafe(info.getHtmlUrl()));
        rb.setIsPrivate(info.isPrivate());
        rb.setLastVisitedAt(LocalDateTime.now());
        repoBrowsingMapper.insert(rb);
    }

    @Override
    public List<RepoBrowsingDTO> listRecent(Long userId, int limit) {
        int actual = limit <= 0 ? 20 : Math.min(limit, MAX_LIMIT);
        List<RepoBrowsing> list = repoBrowsingMapper.selectList(
                new LambdaQueryWrapper<RepoBrowsing>()
                        .eq(RepoBrowsing::getUserId, userId)
                        .orderByDesc(RepoBrowsing::getLastVisitedAt)
                        .last("LIMIT " + actual));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public void deleteById(Long userId, Long id) {
        RepoBrowsing rb = repoBrowsingMapper.selectById(id);
        if (rb == null || !userId.equals(rb.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        repoBrowsingMapper.deleteById(id);
    }

    @Override
    public void clearAll(Long userId) {
        repoBrowsingMapper.delete(
                new LambdaQueryWrapper<RepoBrowsing>().eq(RepoBrowsing::getUserId, userId));
    }

    private RepoBrowsingDTO toDTO(RepoBrowsing rb) {
        return RepoBrowsingDTO.builder()
                .id(rb.getId())
                .repoUrl(rb.getRepoUrl())
                .fullName(rb.getFullName())
                .description(rb.getDescription())
                .language(rb.getLanguage())
                .starCount(rb.getStarCount())
                .ownerAvatarUrl(rb.getOwnerAvatarUrl())
                .htmlUrl(rb.getHtmlUrl())
                .isPrivate(Boolean.TRUE.equals(rb.getIsPrivate()))
                .lastVisitedAt(rb.getLastVisitedAt())
                .build();
    }

    /**
     * 统一仓库 URL：去除前后空白、去除末尾斜杠，避免 "...repo" 与 "...repo/" 产生重复记录。
     */
    private String normalize(String repoUrl) {
        String s = repoUrl.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
