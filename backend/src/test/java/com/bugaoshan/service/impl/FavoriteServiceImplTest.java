package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.dto.FavoriteDTO;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.Favorite;
import com.bugaoshan.entity.Material;
import com.bugaoshan.mapper.CourseMapper;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.MaterialMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceImplTest {

    @Mock private FavoriteMapper favoriteMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private MaterialMapper materialMapper;

    @Test
    void rejectsUnsupportedFavoriteType() {
        FavoriteServiceImpl service = service();

        assertThrows(BusinessException.class,
                () -> service.addFavorite(7L, favorite("post", 3L)));

        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void rejectsInactiveCourse() {
        Course course = new Course();
        course.setStatus(0);
        course.setIsDeleted(0);
        when(courseMapper.selectById(3L)).thenReturn(course);

        assertThrows(BusinessException.class,
                () -> service().addFavorite(7L, favorite("course", 3L)));

        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void rejectsNonPublishedMaterial() {
        Material material = new Material();
        material.setStatus(2);
        material.setIsDeleted(0);
        when(materialMapper.selectById(9L)).thenReturn(material);

        assertThrows(BusinessException.class,
                () -> service().addFavorite(7L, favorite("material", 9L)));

        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void duplicateFavoriteIsIdempotent() {
        Course course = activeCourse();
        Favorite existing = new Favorite();
        when(courseMapper.selectById(3L)).thenReturn(course);
        when(favoriteMapper.selectByUserIdAndTarget(7L, 1, 3L)).thenReturn(existing);

        assertDoesNotThrow(() -> service().addFavorite(7L, favorite("course", 3L)));

        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void insertsPublishedMaterialFavorite() {
        Material material = new Material();
        material.setStatus(1);
        material.setIsDeleted(0);
        when(materialMapper.selectById(9L)).thenReturn(material);

        service().addFavorite(7L, favorite("material", 9L));

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getTargetType());
        assertEquals(9L, captor.getValue().getTargetId());
    }

    @Test
    void favoriteListOmitsInactiveTargets() {
        Favorite favorite = new Favorite();
        favorite.setFavoriteId(1L);
        favorite.setTargetId(3L);
        favorite.setTargetType(1);
        Course inactive = activeCourse();
        inactive.setStatus(0);
        when(favoriteMapper.selectByUserIdAndType(7L, 1)).thenReturn(List.of(favorite));
        when(courseMapper.selectById(3L)).thenReturn(inactive);

        List<?> data = (List<?>) service().getFavoriteList(7L, "course").getData();

        assertEquals(0, data.size());
    }

    @Test
    void removingFavoriteIsScopedToCurrentUser() {
        Favorite existing = new Favorite();
        when(favoriteMapper.selectByUserIdAndTarget(7L, 1, 3L)).thenReturn(existing);

        service().removeFavorite(7L, favorite("course", 3L));

        verify(favoriteMapper).deleteByUserIdAndTarget(7L, 1, 3L);
    }

    private FavoriteServiceImpl service() {
        return new FavoriteServiceImpl(favoriteMapper, courseMapper, materialMapper);
    }

    private FavoriteDTO favorite(String type, Long targetId) {
        FavoriteDTO dto = new FavoriteDTO();
        dto.setType(type);
        dto.setTargetId(targetId);
        return dto;
    }

    private Course activeCourse() {
        Course course = new Course();
        course.setStatus(1);
        course.setIsDeleted(0);
        return course;
    }
}
