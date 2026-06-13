package com.bugaoshan.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PersistenceMappingTest {

    @Test
    void squarePostPrimaryKeyIsMappedForBaseMapperOperations() throws Exception {
        assertNotNull(SquarePost.class.getDeclaredField("postId").getAnnotation(TableId.class));
    }

    @Test
    void squareCommentPrimaryKeyIsMappedForBaseMapperOperations() throws Exception {
        assertNotNull(SquareComment.class.getDeclaredField("commentId").getAnnotation(TableId.class));
    }
}
