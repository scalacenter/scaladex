ALTER TABLE artifacts
ADD COLUMN platform VARCHAR NOT NULL default 'jvm', -- default value which means JAVA
ADD COLUMN language_version VARCHAR NOT NULL default 'Java'; -- a default value for Java platform