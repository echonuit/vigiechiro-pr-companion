## Context

Le lot 38 a corrigé neuf sites lus dans le code, et en a laissé deux. Ses décisions sont dans la demande #5959
et dans la javadoc des classes touchées. Ce changement n'en prend aucune de plus sur le produit.

## Decisions

**Une capacité par écran, pas une capacité « dates ».** C'est le grain que le dépôt s'est donné : une capacité
est un geste dans les termes de l'utilisateur, et « lire la plage horaire d'une nuit » appartient à la fiche
d'un passage, pas à une règle technique de formatage. Confirmé par le porteur le 5 octobre 2026. Cinq capacités
s'ouvrent donc, chacune avec la ou les exigences que ce lot lui donne ; elles s'étofferont quand d'autres lots
toucheront ces écrans.

**Deux exigences pour la colonne « Passage ».** La forme et le tri sont deux comportements : en français,
triée comme un texte, la colonne rangerait le 1er juillet avant le 22 juin. Les deux ont leur test, et un
changement qui tiendrait l'un en cassant l'autre doit se lire.

**Une exigence nomme une forme exacte quand le porteur l'a choisie.** La plage horaire, « 22/06/2026  20:25 ->
07:47 », est la forme qu'il a retenue le 5 octobre : l'exigence la cite telle quelle.

**L'heure du site, pour une nuit reconstruite.** Le compte rendu d'une reconstruction part d'un instant de la
plateforme, avec son décalage. L'exigence dit l'heure murale du site, et non celle du poste : c'est ce que le
test affirme, et ce qui distingue cette date des autres.

## Risks / Trade-offs

Cinq capacités neuves ne portent chacune qu'une à trois exigences : leur `Purpose` dit l'écran entier, et leur
contenu n'en décrit qu'une part. C'est assumé, et dit dans chaque `Purpose`.
