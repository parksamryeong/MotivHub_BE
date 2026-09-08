CREATE TABLE issue_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_issue_comment_issue FOREIGN KEY (issue_id) REFERENCES issue(id),
    CONSTRAINT fk_issue_comment_author FOREIGN KEY (author_id) REFERENCES user(id)
);
