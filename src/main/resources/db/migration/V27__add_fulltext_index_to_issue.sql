ALTER TABLE issue ADD FULLTEXT INDEX ft_issue_search (title, problem_description, solution) WITH PARSER ngram;
