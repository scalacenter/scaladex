# How to improve the visibility of your project

As a library author or maintainer, the visibility of your project on Scaladex is under your control.

As soon as an artifact is pushed to Maven Central (Sonatype), Scaladex downloads the `pom.xml` from Maven Central and the repository metadata from Github.
The following properties are taken into account by the search algorithm, with corresponding boost.
The bigger is the boost, the more important the property.
 

| property                              | boost |
| ------------------------------------- | --------- |
| repository name on Github             | 6         |
| repository organization on Github     | 5         |
| repository description on Github      | 4         |
| list of topics on Github              | 4         |
| name of the artifacts in Maven Central| 2         |
| project README on Github              | 0.5       |

You can make it easier for a user to find your project by paying attention to the below details:

### Meaningful Github description

A good description is short but meaningful.
Keep in mind that those words are the first words a user will read about your project.

### Precise Github topics

Carefully choosing the topics of your project is the most efficient way to improve your discoverability.
You should ask yourself what terms a developer is likely to type when looking for a solution that your library provides.
Be precise.
If your project is a collection of utilities the term `utils` is not much helpful but `time`, `duration`, `units`, `hashing` are.

### Detailed README

A detailed readme is helpful but it is not required for good indexing.
