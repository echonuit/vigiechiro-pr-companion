# Préparer le dépôt

L'écran **Préparer le dépôt** prépare et trace le **dépôt** d'une nuit vérifiée sur la
plateforme Vigie-Chiro. Le dépôt suit un **flux ordonné**, rappelé en haut de l'écran par un fil
d'étapes (l'étape courante est mise en avant). Le nombre d'étapes dépend de la **forme du dépôt**,
choisie dans [Réglages](reglages.md) ▸ Dépôt :

| Forme du dépôt | Les étapes |
|---|---|
| **Séquences WAV** (par défaut), application connectée | **« 1 · Préparer »**, **« 2 · Téléverser »**, **« 3 · Marquer déposé »** |
| **Archives ZIP**, ou application non connectée | **« 1 · Préparer »**, **« 2 · Générer les archives »**, **« 3 · Téléverser »**, **« 4 · Marquer déposé »** |

La **dernière étape** porte le nom de son bouton. Elle s'appelle « Marquer déposé » tant qu'aucune
participation n'est liée au passage, et **« Lancer la participation »** dès que l'application a déposé la
nuit elle-même. Elle reste alors l'étape courante tant que la participation n'est pas lancée : une nuit
déposée n'est pas une nuit traitée. Elle n'est franchie qu'une fois l'analyse demandée à Vigie-Chiro.

En séquences WAV, rien ne produit d'archive : l'étape « Générer les archives » n'a pas lieu d'être, et
l'écran ne l'affiche pas. Elle revient si vous choisissez les archives ZIP, et elle reste quand
l'application n'est pas connectée, parce qu'elle sert alors au dépôt manuel. Elle revient aussi, sans
numéro, quand Vigie-Chiro refuse des séquences sans recours : c'est le
[repli manuel](#le-repli-manuel-quand-des-sequences-sont-refusees-sans-recours).

Un dépôt **déjà entamé garde sa forme** : si une archive de la nuit est en ligne, l'écran reste celui
des archives, quoi que dise le réglage.

![L'écran en forme par défaut, prêt à déposer : trois étapes, et « Téléverser sur Vigie-Chiro » pour la deuxième.](../assets/captures/apercu-lot-sequences.png)

## Vérifier et préparer le dépôt

![L'écran Préparer le dépôt, application non connectée : la checklist de cohérence, et la première étape « Vérifier et préparer le dépôt » à faire.](../assets/captures/apercu-lot-preparer.png)

Le **récapitulatif**, dans la barre de statut, indique le nombre de séquences et le volume. Une **checklist de cohérence** montre,
contrôle par contrôle et **même quand tout est satisfait**, ce qui est vérifié : transformation
effectuée, fichiers bien nommés, journal du capteur présent, relevé climatique. Chaque ligne est marquée
**✓** (satisfait), **✗** (à corriger, bloquant) ou **⚠** (avertissement non bloquant, comme un relevé
climatique absent). « Vérifier et préparer le dépôt » **verrouille** ensuite la liste des séquences qui
partiront. Vos fichiers d'origine ne sont pas modifiés. Le passage passe alors au statut « Prêt à
déposer ».

## Générer les archives de dépôt (archives ZIP, ou sans connexion)

Cette étape n'apparaît pas pour un dépôt en séquences WAV fait par l'application.

![L'état « Prêt à déposer », application non connectée : « Générer les archives » est l'étape courante, et l'étape 3 n'offre que le dépôt manuel. L'écran est le même quelle que soit la forme réglée.](../assets/captures/apercu-lot-deposer.png)

En forme ZIP, ce que l'on téléverse sur Vigie-Chiro, ce sont des **archives** (au plus 700 Mo par défaut, réglable dans [Réglages](reglages.md)), découpées depuis les
séquences et écrites dans le sous-dossier `depot/` de la session. La génération peut être **longue**
sur une grosse nuit : elle s'exécute en arrière-plan, avec un indicateur d'activité, et les actions
sont neutralisées le temps de l'écriture (on ne risque pas de téléverser une archive incomplète).

!!! tip "Si vous êtes connecté, cette étape est facultative"
    Le téléversement **produit lui-même les archives dont il a besoin**, au fur et à
    mesure, et les efface du disque dès qu'elles sont en ligne. Vous pouvez donc passer directement de
    la préparation au téléversement : le fil d'étapes indique d'ailleurs « 3 · Téléverser » comme étape
    courante, sans archive sur le disque.

    Générer d'abord reste utile pour un **dépôt manuel** (hors connexion, ou pour déposer depuis le
    site web) : c'est le seul cas où il faut les archives complètes sur votre machine.

    Conséquence directe : il n'est plus nécessaire d'avoir la place pour **toutes** les archives à la
    fois. Le téléversement n'en garde que **deux** sur le disque à un instant donné.

    C'est pourquoi le tableau de cette étape reste **vide pendant et après un téléversement** : il ne
    liste que les archives conservées sur votre machine. Il le dit, et vous renvoie à l'étape de
    téléversement, qui suit celles qui partent.

![Génération des archives en cours, application non connectée : indicateur d'activité, actions désactivées.](../assets/captures/apercu-lot-generation.png)

Le tableau de suivi des archives laisse **choisir et réordonner ses colonnes** (clic droit ou menu principal (☰)
« outils ») : voir [Personnaliser les tableaux](../personnaliser-les-tableaux.md). La **table de dépôt**
de l'étape de téléversement offre le même réglage, mémorisé séparément.

## Téléverser sur Vigie-Chiro

En séquences WAV, l'application envoie les **séquences transformées une à une**, plusieurs à la fois.
Il n'y a rien à générer avant, et l'étape ne propose pas de dépôt manuel : pour déposer à la main,
choisissez les archives ZIP dans les réglages. Ce qui suit décrit l'étape en forme ZIP ; la table de
dépôt, la reprise et le compte rendu valent pour les deux formes.

![Archives générées, application non connectée : la liste des ZIP s'affiche, « Ouvrir le dossier » s'active, et le dépôt manuel est le seul chemin offert.](../assets/captures/apercu-lot-archives.png)

Connecté, vous pouvez téléverser **sans avoir rien généré** : le téléversement est déjà l'étape
courante, et la table des archives est vide.

![Le téléversement est l'étape courante sans archives : le téléversement produit lui-même ce dont il a besoin.](../assets/captures/apercu-lot-televerser-sans-archives.png)

Deux chemins s'offrent à vous :

- **Téléversement automatique** (application connectée à Vigie-Chiro) : le bouton
  **« Téléverser sur Vigie-Chiro »** dépose la nuit directement : la participation est créée (ou
  réutilisée si elle l'a été à l'import), puis les **archives ZIP**, ou les séquences WAV, sont
  téléversées **plusieurs à la fois** (5 en parallèle), ce qui raccourcit nettement le dépôt d'une grosse
  nuit. Une **table de dépôt** suit chaque fichier (en attente → en cours → déposé, ou échec avec la
  raison au survol) avec une **barre de progression par fichier** qui reflète les octets réellement envoyés, et la **barre de
  statut** en bas de la fenêtre affiche l'avancement d'ensemble en continu, même quand vous faites
  défiler l'écran.

    L'application **ne change jamais de forme à votre place**. Si l'espace disque ne permet pas de
    produire les archives, le dépôt en ZIP est refusé en le disant, et vous choisissez : libérer de
    l'espace, ou passer aux séquences WAV dans les réglages.
- **Téléversement manuel** (forme ZIP, ou sans connexion) : **« Ouvrir le dossier »** ouvre le sous-dossier
  `depot/` dans le gestionnaire de fichiers, et vous déposez les archives sur Vigie-Chiro depuis votre
  navigateur.

    Le chemin de ce dossier est affiché juste au-dessus, avec un bouton **« Copier »** qui le place
    dans le presse-papier : vous le collez tel quel dans l'explorateur de fichiers ou dans le
    formulaire de la plateforme, sans le recopier à l'œil. Il est grisé tant qu'il n'y a pas de chemin
    à copier.

### Si le site n'est pas rattaché, le refus dit quoi faire

Déposer exige que votre site porte un **lien** vers son homologue Vigie-Chiro. Sans lui, le
téléversement s'arrête net - et le message ne se contente pas de le constater : il demande à la
plateforme si votre carré y existe, puis vous oriente en conséquence.

![Le bandeau de refus au-dessus des archives : le site n'est pas rattaché, le carré existe sur Vigie-Chiro, et le message indique par quel geste le récupérer.](../assets/captures/apercu-lot-refus-rattachement.png)

- le carré **existe** là-bas en Point Fixe : récupérez-le depuis « Mes sites » › « Nouveau site »
  (voir [Mes sites](sites.md#recuperer-un-carre-qui-existe-deja)), et le rattachement vient avec ;
- il n'y **est pas** : activez-le d'abord sur le portail Vigie-Chiro, où il faut créer un point ;
- la plateforme **ne répond pas** : le message vous le dit, plutôt que de trancher à votre place.

### Un dépôt interrompu se reprend

Le dépôt automatique est **reprenable** : une coupure réseau, une fermeture de l'application ou un
échec partiel ne font **rien perdre**. Le passage prend le statut « **Dépôt en cours** » et, à la
réouverture de l'écran, la table de dépôt réaffiche l'état exact de chaque fichier. Le bouton devient
alors « **Reprendre le dépôt** » : seuls les fichiers manquants sont re-téléversés, jamais ceux déjà
en ligne. Le passage ne devient « Déposé » que lorsque **tous** les fichiers sont en ligne.

### Certains refus ne se reprennent pas

Toutes les archives en échec ne sont pas dans le même cas. Une coupure réseau, une lenteur du serveur
ou une interruption de votre part laissent l'archive **reprenable** : la relancer a toutes les chances
d'aboutir.

Mais quand Vigie-Chiro **refuse** une archive, la renvoyer telle quelle serait refusée de la même
façon. L'application ne vous propose donc plus de la reprendre, et cela se voit : le bouton cesse de
s'appeler « Reprendre le dépôt » et redevient « **Téléverser sur Vigie-Chiro** », parce qu'il ne reste
plus rien à reprendre.

La table garde le détail : la cause de chaque échec y est lisible, archive par archive.

![Le compte rendu d'un dépôt incomplet : 11 archives sur 15 en ligne, quatre refusées par Vigie-Chiro. Deux redeviendront reprenables après une reconnexion, une a été refusée par le stockage et se relance, une a un contenu refusé et demande de régénérer les archives.](../assets/captures/apercu-lot-depot-refus-definitif.png)

#### Ce qui peut lever un refus, et ce qui ne le peut pas

| Ce que dit le refus | Ce qui le lève |
|---|---|
| **droits ou jeton** (session expirée, autorisation manquante) | **vous reconnecter**. Les archives refusées pour cette raison redeviennent reprenables aussitôt, sans autre geste |
| **contenu refusé** (l'archive ou la séquence elle-même ne convient pas) | se reconnecter n'y change rien. Pour des archives, il faut **régénérer les archives** de la nuit, puis relancer le téléversement. Pour des séquences, l'écran offre le [repli manuel](#le-repli-manuel-quand-des-sequences-sont-refusees-sans-recours) |
| **stockage** (l'espace de stockage de Vigie-Chiro refuse l'envoi des octets) | se reconnecter n'y change rien : **relancez le téléversement**, qui redemande de nouvelles autorisations d'envoi. Si le refus persiste : pour des archives, déposez-les depuis le dossier de la nuit, comme le permet le dépôt manuel ; pour des séquences, l'écran offre le [repli manuel](#le-repli-manuel-quand-des-sequences-sont-refusees-sans-recours) |

!!! tip "Après une régénération, relancez simplement le téléversement"
    Le bouton s'appelle alors « Téléverser sur Vigie-Chiro » et non « Reprendre le dépôt » : c'est
    normal, il n'y a plus rien à *reprendre*. Le cliquer renvoie tout de même les archives refusées,
    et celles qui viennent d'être régénérées passeront cette fois. Les archives déjà en ligne, elles,
    ne repartent pas.

    **« Générer les archives de dépôt » reste disponible pendant un dépôt entamé**, pour cette raison
    même : c'est le geste qu'un contenu refusé demande. Il n'est refusé que **pendant** un
    téléversement, parce que celui-ci produit lui-même ses archives dans le même dossier : le message
    vous dit alors d'attendre la fin du téléversement ou de l'annuler.

![Un dépôt entamé : l'en-tête dit « Dépôt Vigie-Chiro entamé », le téléversement reste l'étape courante, et « Générer les archives de dépôt » reste offert.](../assets/captures/apercu-lot-depot-entame.png)

![Pendant un téléversement, la génération est refusée : le bandeau demande d'attendre la fin du téléversement ou de l'annuler, et « Annuler le dépôt » est offert au-dessus de la table de suivi.](../assets/captures/apercu-lot-generation-refusee.png)

#### Le repli manuel, quand des séquences sont refusées sans recours

En séquences WAV, l'écran n'offre ni archives ni dépôt manuel : tant que le téléversement passe, ils ne
servent pas. Si Vigie-Chiro refuse des séquences pour une raison que ni « Reprendre le dépôt » ni une
reconnexion ne lèveront, c'est-à-dire un refus du stockage ou un contenu refusé, une carte
**« Repli : déposer à la main »** apparaît **sous** l'étape de téléversement.

Elle dit combien de séquences ont été refusées, et offre « **Générer les archives de dépôt** » : les
archives ZIP des séquences qui ne sont pas en ligne sont écrites dans le sous-dossier `depot/`. Le chemin de ce dossier et
« Ouvrir le dossier (dépôt manuel) » reviennent avec elle, dans l'étape de téléversement. Déposez ces
archives à la main sur le portail, puis cliquez « **Marquer le passage déposé** », dans la même carte :
le bouton s'ouvre une fois les archives générées. Il reste alors à « Lancer la participation », comme
après tout dépôt.

Le fil d'étapes ne change pas : il compte toujours trois étapes. Le repli n'est pas une étape de plus,
c'est l'issue de celle qui vient d'être refusée.

Les archives ne contiennent que les séquences que Vigie-Chiro **n'a pas** : celles qui sont déjà en ligne
n'y sont pas remises, parce que la plateforme les garderait en double.

Un refus de **droits** n'offre pas le repli, puisqu'il suffit de vous reconnecter. Un échec que
« Reprendre le dépôt » peut rattraper ne l'offre pas non plus.

En ligne de commande, `deposer-vigiechiro` nomme le même repli sous son bilan :
`exporter-lot --passage N` génère les archives, et `deposer --passage N` marque le passage une fois
qu'elles sont sur le portail.

![Le repli manuel : une séquence en ligne, deux refusées par le stockage, et sous l'étape de téléversement la carte « Repli : déposer à la main » avec « Générer les archives de dépôt » et « Marquer le passage déposé ».](../assets/captures/apercu-lot-repli-manuel.png)

### Ce que le dépôt vous rend à la fin

Quand le téléversement se termine, un **compte rendu** dit ce qui est en ligne, en proportions.

![Le compte rendu d'un dépôt complet : la barre est pleine, le volume téléversé est dit, et l'étape suivante est nommée.](../assets/captures/apercu-lot-depot-compte-rendu.png)

Le compte rendu **nomme ce qui est parti** : des archives pour un dépôt en ZIP, des séquences pour un
dépôt en WAV.

![Le compte rendu d'un dépôt complet en séquences WAV : il compte des séquences, pas des archives.](../assets/captures/apercu-lot-depot-compte-rendu-sequences.png)

Il ne remplace pas la table : celle-ci garde le **détail par fichier**, avec la cause de chaque échec.
Le compte rendu répond à la question qu'on se pose à cet instant - **quelle part est arrivée** - et il
ajoute deux choses que la table ne donne pas : le **volume téléversé**, et **ce qu'il reste à faire**.

Quand **tout** est en ligne, il dit qu'il reste à **lancer la participation** : téléverser ne suffit
pas, et rien ne le disait à ce moment-là. Le bouton qui le fait est celui de la dernière étape, juste en
dessous, et lui seul : le compte rendu ne le double pas.

Si vous avez **arrêté** le dépôt en cours de route, le compte rendu le dit sans le déguiser :

![Le compte rendu d'un dépôt interrompu : la part restante apparaît, et la reprise est annoncée.](../assets/captures/apercu-lot-depot-interrompu.png)

La part **« Restantes »** est la différence entre ce que vous avez arrêté et un dépôt réussi : sans
elle, la barre serait pleine alors qu'il manque des fichiers sur la plateforme. Aucune action n'est
proposée en pied, parce que la suite est **« Reprendre le dépôt »**, un bouton déjà sous vos yeux.

## Lancer la participation (ou marquer le passage déposé)

C'est la dernière étape : la quatrième en forme ZIP, la troisième en séquences WAV.

![L'état « Déposé » après un dépôt manuel : toutes les étapes sont franchies, et la dernière s'appelle « Marquer déposé ».](../assets/captures/apercu-lot-depose.png)

Le bouton de cette dernière étape **change selon votre situation**.

**Vous avez téléversé depuis l'application** (une participation est rattachée à la nuit) : l'étape
s'intitule **« 4. Lancer la participation »**, et son bouton aussi. Il demande à Vigie-Chiro de **traiter** les fichiers que
vous venez de déposer : la plateforme décompresse les archives, puis lance l'identification Tadarida.

!!! warning "Téléverser ne suffit pas : il faut lancer la participation"
    Tant que vous ne l'avez pas cliqué, vos fichiers sont bien **sur la plateforme**, mais **aucun
    traitement n'est lancé** : la participation reste vide sur le site web et aucun résultat
    n'arrivera. C'est une action volontaire, et le seul moyen de déclencher le calcul depuis
    l'application (vous pouvez aussi le faire depuis la page de la participation, sur le site).

Ce que Vigie-Chiro répond s'affiche **juste sous le bouton** : la demande est acceptée, l'analyse était
déjà demandée, ou la plateforme refuse, et elle dit alors pourquoi.

![Après « Lancer la participation » : la dernière étape dit, sous son bouton grisé, que l'analyse est demandée, et la carte « Traitement Vigie-Chiro » la montre planifiée.](../assets/captures/apercu-lot-lancement-accepte.png)

Tant que l'analyse est **planifiée, en cours ou relancée** par la plateforme, le bouton reste grisé :
il n'y a rien à relancer, et la carte « Traitement Vigie-Chiro » ci-dessous dit où en est le calcul.
L'application le sait par le dernier état relevé, y compris quand vous rouvrez l'écran.

![Un refus : la dernière étape cite le motif que la plateforme a donné.](../assets/captures/apercu-lot-lancement-refuse.png)

**Vous avez téléversé depuis le navigateur** (repli manuel) : le bouton reste **« Marquer
déposé »**. Il fait passer le passage au statut « Déposé » (ce qui déverrouille la validation
Tadarida) et trace la date du dépôt : c'est une **écriture locale**, l'application ne peut pas deviner
seule ce que vous avez déposé à la main.

## Suivre l'analyse : la carte « Traitement Vigie-Chiro »

Déposer n'est pas la fin. Une fois la participation lancée, la plateforme **analyse la nuit** avec
Tadarida, et **les observations n'arrivent qu'une fois cette analyse terminée**. La carte
« Traitement Vigie-Chiro » apparaît sous la dernière étape dès que l'application a déposé la nuit, et
vous dit où en est le calcul :

| Ce que la carte affiche | Ce que cela veut dire |
|---|---|
| **Analyse planifiée** | La demande est enregistrée, un calculateur va la prendre en charge. |
| **Analyse en cours** | Le calcul tourne. Comptez plusieurs dizaines de minutes. |
| **Analyse terminée** | Les observations sont prêtes. « Actualiser » les importe : voir ci-dessous. |
| **Un premier essai a échoué…** | La plateforme a relancé le calcul d'elle-même. Patientez. |
| **L'analyse a échoué** | Le motif est indiqué. |

Les heures de la carte sont celles de votre poste, même si la plateforme les donne en temps universel.

![La carte « Traitement Vigie-Chiro » sur une analyse en cours, avec son heure de départ.](../assets/captures/apercu-lot-traitement-en-cours.png)

!!! note "« URL de stockage refusée »"
    Vos enregistrements montent vers un espace de stockage dont **la plateforme fournit l'adresse**.
    L'application vérifie cette adresse avant d'y envoyer quoi que ce soit : si elle n'est pas celle
    attendue, ou si elle n'est pas chiffrée (`https`), le dépôt est **refusé sans rien envoyer**.

    Ce refus est anormal : il signifie que l'hébergement de Vigie-Chiro a changé, ou que quelque chose
    s'interpose. **Signalez-le** plutôt que de contourner. Rien n'a été envoyé, et votre nuit est
    intacte : le dépôt pourra être relancé une fois la cause connue.

L'application **n'interroge pas la plateforme en permanence** : elle affiche le dernier état qu'elle
connaît, en précisant de quand il date : y compris hors connexion. Le bouton **« Actualiser »**
redemande l'état à Vigie-Chiro, et vous pouvez fermer l'application entre-temps : le calcul se
poursuit sur le serveur.

**Quand « Actualiser » apprend que l'analyse est terminée, les observations sont importées dans le
même geste.** Il n'y a pas à passer par « Sons & validation » : la carte affiche, sous l'état, ce qui
vient d'arriver.

| Ce que la carte ajoute sous « Analyse terminée » | Ce que cela veut dire |
|---|---|
| **Observations importées depuis Vigie-Chiro : …** | L'import vient d'avoir lieu ; le nombre d'observations est indiqué. |
| **Les observations de cette nuit sont déjà importées** | Rien n'a été réimporté. Pour les remplacer, passez par [« Sons & validation »](validation.md). |
| **L'import des observations a échoué : …** | L'analyse reste terminée. Cliquez de nouveau « Actualiser », ou importez depuis « Sons & validation ». |
| **Cliquez « Actualiser » pour importer les observations** | L'écran vient de s'ouvrir sur un état déjà connu : rien n'est importé tant que vous ne le demandez pas. |

![La carte « Traitement Vigie-Chiro » après un « Actualiser » sur une analyse terminée : les observations sont importées.](../assets/captures/apercu-lot-traitement-termine.png)

L'application ne remplace jamais, depuis cette carte, des observations que vous avez déjà : vos
validations s'y trouvent.

Si une analyse **traîne depuis plus de 24 h**, la carte vous le signale : elle semble bloquée, et il
peut valoir la peine de la relancer.

!!! danger "Une nuit déjà analysée ne se relance pas"
    Une fois l'analyse terminée, le bouton « Lancer la participation » se **verrouille**. Ce n'est pas
    une limitation arbitraire : relancer un calcul **efface d'abord les observations** côté serveur
    pour les recalculer. Pour un dépôt en **archives ZIP**, l'audio n'est **pas conservé** par la
    plateforme : le recalcul rendrait une participation **vide, définitivement**. Pour un dépôt en
    **séquences WAV**, l'audio est conservé et le recalcul est possible, mais les observations sont
    effacées le temps qu'il aboutisse : le verrou reste, et l'infobulle du bouton dit laquelle de ces
    deux raisons vaut pour votre nuit.

    Si vous devez tout de même relancer (typiquement après un échec, où il n'y a plus rien à perdre),
    cela reste possible en ligne de commande, délibérément :
    `vigiechiro lancer-traitement-vigiechiro --passage <id> --forcer`.

### Recommencer un dépôt de zéro

Le bouton **« Réinitialiser le dépôt »** (visible dès qu'un dépôt a été entamé) **efface le suivi
local** : l'application oublie ce qu'elle croit avoir déposé et le passage revient à « Prêt à
déposer ». Le téléversement suivant repart alors **de zéro**, toutes archives comprises.

Il est utile quand le suivi local ne correspond plus à la réalité de la plateforme : typiquement si
une nuit apparaît « Déposée » côté application alors que la participation est vide côté site web. Vos
**archives sur le disque** et le **lien vers la participation** sont conservés : rien n'est perdu, on
ne remet à zéro que le compteur.

## La barre de statut : l'état du dépôt en permanence

L'écran est long ; la **barre de statut** du bas de fenêtre garde l'essentiel sous les yeux :

- à **gauche**, le contexte (« Carré 640380 · A1 · N° 2 ») ;
- au **centre**, le statut et le récapitulatif (« Prêt à déposer · 4806 séquences · 13,2 Go ») ;
- à **droite**, l'état vivant : d'abord l'avertissement de réconciliation s'il y en a un, puis la
  progression du dépôt, sinon celle de la génération d'archives (avec l'estimation du temps restant),
  sinon une alerte d'espace disque, sinon le bilan des archives présentes (« 21 archive(s) · 5,9 Go
  dans depot/ »).

!!! warning "« Déjà déposées : impossible à vérifier »"

    Avant de téléverser, l'application demande à Vigie-Chiro ce qui s'y trouve déjà, pour ne pas
    renvoyer des archives inutilement. Quand cette question **ne peut pas être posée** (connexion
    coupée, session expirée), le dépôt continue quand même, mais des archives déjà en ligne vont
    repartir.

    L'avertissement le dit **avant** que vous attendiez, et non après, parce que c'est le seul moment
    où vous pouvez encore décider d'arrêter et de réessayer plus tard. Il passe donc devant
    « n/N déposées » : l'avancement ne vous apprend rien que vous puissiez utiliser, celui-ci si.

    La cause est nommée : une coupure se réessaie, une session expirée demande de se reconnecter.

## Checklist de cohérence : ce qui bloque

Si la nuit n'est pas en état d'être déposée (par exemple séquences d'écoute absentes ou journal du
capteur manquant), les contrôles concernés passent en **✗** dans la checklist, avec la raison et la
correction à apporter. La suite du dépôt est neutralisée tant qu'un contrôle est en échec. Le bouton
« Vérifier et préparer le dépôt » reste offert : il relance la vérification une fois la correction faite.
Un **⚠** (relevé climatique absent) n'empêche pas, lui, de préparer le dépôt.

![L'état incohérent : la checklist montre les contrôles ✓ et ✗, l'en-tête demande de les corriger, et les étapes suivantes sont grisées.](../assets/captures/apercu-lot-alertes.png)
