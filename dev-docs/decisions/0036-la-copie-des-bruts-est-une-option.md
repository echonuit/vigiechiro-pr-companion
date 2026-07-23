# ADR 0036 — La copie des enregistrements bruts est une option de ré-analyse, pas un défaut

- **Statut** : Accepté — 2026-07-20
- **Chantier** : EPIC #2061 (lots #2062 à #2064)
- **Vérification** : humaine — la copie des bruts comme option non par défaut est un arbitrage produit, vérifié en revue et par les tests d'import

## Contexte

L'import copiait les WAV bruts dans `bruts/` avant de les transformer, et ce depuis toujours. Une case de l'écran d'import permettait de s'en passer, décochée par presque personne.

Ce défaut coûte cher : les bruts pèsent **plusieurs gigaoctets par nuit**, soit un tiers à la moitié de l'occupation d'un import, et la copie SD → workspace représente **65 à 70 % du temps d'import** (`docs/benchmarks/README.md`). Ne pas copier divise donc ce temps par environ trois, en plus de la place gagnée.

Et il n'avait **aucune justification écrite**. Le code disait « comportement historique » ; la doc utilisateur parlait d'« archive de sécurité ». Aucune ADR, aucune exigence du protocole Vigie-Chiro ne l'imposait.

Trois objections se présentent spontanément. Aucune ne tient, et c'est pour cela que cette décision mérite d'être écrite.

**« R9 l'impose. »** Non. R9 dit : *ne jamais écrire sur la carte SD*. Le mode sans copie lit les WAV **en place** et est documenté partout comme *conforme* à R9 (`PreparationOriginaux`, `SourceOriginal`, `ServiceImport`, `InspecteurDossier`). La garantie effectivement testée est la non-modification de la source, pas la conservation d'une copie.

**« On ne pourra plus récupérer une nuit archivée. »** Non plus. **Rien dans l'application ne relit `bruts/` après l'import.** Les deux voies de réactivation partent d'un **dossier désigné par l'utilisateur**, et le code anticipe explicitement la carte SD comme provenance :

> `CandidatsReactivation` — sous l'un des deux noms sous lesquels un utilisateur le garde : son nom R6 (copie du dossier `bruts/`) ou son **nom d'enregistreur non préfixé (copie de la carte SD)**.

L'[ADR 0006](0006-depot-zip-par-defaut-perte-audio-serveur-assumee.md) désignait d'ailleurs déjà « la **carte SD de l'utilisateur** » comme source de récupération, pas le workspace.

**« On perd la preuve d'identité. »** Non : `sha256` et `size_bytes` sont capturés à l'import dans les **deux** modes. Ils décrivent ce qu'il faut *retrouver*, pas ce qu'on possède — `VerificationIdentiteAudio` le dit : « quand l'utilisateur **n'a gardé que ses originaux**, leur `sha256` intégral prouve l'original ». Ces colonnes sont plus utiles quand le fichier local n'est plus là que quand il y est.

## Décision

**1. Le défaut est de ne pas copier.** Conserver les bruts devient une option, pour qui compte **ré-analyser** ses enregistrements avec d'autres réglages ou un autre outil. Ce n'est pas un service rendu à tout le monde : l'application n'en a aucun besoin pour écouter, valider ou déposer.

**2. L'option nomme son usage, pas sa mécanique.** « Conserver les originaux pour ré-analyse ultérieure », et non « conserver les originaux importés ». Un réglage qui dit à quoi il sert se trouve ; un réglage qui décrit son implémentation ne se trouve pas. Son aide énonce ce qu'il **coûte** — plusieurs Go, import trois fois plus long — et pas seulement ce qu'il fait.

**3. Le choix vit aux Réglages, pas sur l'écran d'import.** L'utilisateur courant n'a rien à arbitrer au moment d'importer ; celui qui a besoin de l'option ira la chercher. Il n'y avait d'ailleurs pas deux mécanismes : la case et le réglage partageaient déjà la même clé.

**4. Rien n'avertit l'utilisateur qu'on ne copie pas.** C'est la partie la moins évidente, et elle tient au **modèle mental** : personne ne s'imagine que l'application garde un **double** d'un fichier qu'elle ne touche jamais. Prévenir qu'on ne fait pas une chose que personne n'attendait, c'est du bruit.

**5. Un choix déjà fait n'est pas repris.** `lireBooleen` ne retombe sur le défaut que si la clé est absente : une installation qui a déjà importé garde sa valeur. On ne revient pas dans le dos de quelqu'un sur une décision explicite ; seules les installations neuves basculent.

**6. Un import sans copie déclare « non stocké localement ».** Les originaux sont **connus et prouvés** mais absents du disque : c'est exactement ce que `originals_purged_at` déclare, et l'[ADR 0001](0001-reactivation-passage-reconstruit-identite-structurelle.md) emploie déjà « purgé » en ce sens pour les originaux adoptés après hydratation. Sans cette déclaration, l'audit prendrait chaque original devenu introuvable pour une **corruption** — un fait voulu passant pour un incident, ce que l'[ADR 0012](0012-audit-coherence-tout-ecart-visible-etat-normal-silencieux.md) proscrit.

**7. Le réglage appartient à l'appelant, pas au service.** IHM et CLI lisent le réglage et disent au service ce qu'ils veulent. `ServiceImport` n'a pas à connaître une préférence d'interface : une première version lui injectait le réglage, PMD l'a refusée pour liste de paramètres excessive, et le motif de fond était le bon.

## Conséquences

Le pic disque de l'import passe de 2-3× la taille source à 1-2×, et l'import est environ trois fois plus rapide. Cela réduit d'autant la portée de #2041, qui ne garde que son garde-fou disque.

Le bouton « Purger les originaux » **n'est pas supprimé** : il se retire déjà seul quand il n'y a rien à purger, et le supprimer priverait les installations existantes du seul moyen de récupérer l'espace de leurs bruts déjà copiés. La purge n'est d'ailleurs pas qu'un geste : c'est le **fait déclaré** dont l'audit dépend.

`EnregistrementOriginal.cheminFichier` désigne, en mode sans copie, un chemin sur la carte SD : une **provenance**, pas un localisateur. Aucun parcours de récupération ne s'en sert — la réactivation apparie par nom. Sa doc le dit désormais, pour que personne n'écrive de code supposant ce fichier ouvrable.

## Ce qui a été écarté

**Migrer les installations existantes vers le nouveau défaut.** Techniquement trivial, mais cela reviendrait sur un choix que l'utilisateur a explicitement fait. Reste possible si le besoin apparaît.

**Effacer le `file_path` en mode sans copie.** Plus propre en apparence, mais cela introduirait un `null` là où `BackfillEmpreintes` fait `Path.of(...)`, pour supprimer une information dont la valeur archivistique est réelle.

**Supprimer le mode de conservation.** Le besoin de ré-analyse est légitime pour un utilisateur scientifique ; c'est son statut de **défaut** qui ne l'était pas.
