# P17 - Reprendre une nuit sur un autre poste 🧳

[← Retour au sommaire des parcours](index.md) · **Section C - Après le dépôt & exploitation**

!!! warning "Cible non livrée, et périmètre non tranché"
    Ce parcours décrit un chantier ouvert, pas l'application d'aujourd'hui. Son état se lit sur
    l'issue **#3848**, qui se ferme quand il est livré. Son premier lot rend une **décision
    d'architecture**, pas du code : les questions ci-dessous doivent être tranchées avant d'écrire.

> **Persona principal** : Samuel (poste de terrain, poste de bureau) et Karim (confier une nuit à un
> collègue). **Objectifs qualité visés** : facilité de remplacement, intégrité des annotations.

Samuel récupère ses cartes sur un portable durci, dans une camionnette. Le travail de qualification
et de validation, lui, se fait au bureau, sur un écran calibré et avec un casque correct. Entre les
deux, il n'a aucun moyen de faire passer **une nuit**.

Deux mécanismes existent, et ni l'un ni l'autre ne répond :

| Ce qui existe | Ce que cela fait | Pourquoi cela ne convient pas |
|---|---|---|
| Sauvegarde et restauration | copie la base entière et l'audio | tout ou rien : restaurer chez un collègue écrase son propre travail |
| [P13 - Envoyer un sous-ensemble à un expert](P13%20-%20Envoyer%20un%20sous-ensemble%20a%20un%20expert.md) | une archive de séquences et un CSV filtré | conçu pour faire **écouter** ; rien ne se réimporte |

Il n'y a donc rien entre l'installation complète et une sélection d'observations en lecture seule.

1. Depuis « Carte & passages », Samuel coche une ou plusieurs nuits et choisit « **Préparer un paquet
   de reprise** ».
2. Une fenêtre annonce ce que le paquet contiendra et **combien il pèsera**, avant d'écrire quoi que
   ce soit. Il décide d'y mettre ou non les enregistrements bruts.
3. Il obtient un fichier unique horodaté, qu'il copie sur une clé.
4. Au bureau, il ouvre l'assistant d'import et choisit le paquet comme source. Le contenu est
   **vérifié avant intégration**, comme le fait déjà la restauration.
5. Un compte rendu dit ce qui a été repris. Si la nuit existe déjà sur ce poste, le comportement est
   défini et **annoncé avant** l'écriture, jamais découvert après.

## Les trois questions à trancher

**Copie ou transfert.** La nuit emportée reste-t-elle sur le poste d'origine ? Si oui, deux copies
vivent en parallèle et divergeront dès la première validation. Si non, il faut un mécanisme de remise,
et l'assumer. Ce choix commande tous les autres.

**Ce que le paquet contient.** Les séquences transformées suffisent-elles, ou faut-il les bruts pour
que la nuit reste réactivable ailleurs ? Faut-il embarquer les identifiants de la plateforme, ce qui
permet de poursuivre le dépôt depuis l'autre poste, mais fait circuler un lien vers un compte qui
n'est pas celui du destinataire ?

**Le retour.** Une nuit relue par un collègue revient avec des validations. Est-ce le même paquet qui
repart, une fusion de corrections, ou rien du tout au premier lot ?

## Ce qui est déjà en place

Le mécanisme d'archive est éprouvé : la préparation du dépôt compacte déjà des nuits entières, P13
produit déjà une archive filtrée, et la restauration sait déjà vérifier un contenu avant de basculer
dessus. Ce qui manque relève du périmètre, pas de la technique, et c'est la raison pour laquelle ce
parcours s'ouvre par une phase d'instruction.
