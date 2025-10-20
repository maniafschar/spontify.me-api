SELECT
	ai.id,
	ai.question,
	ai.answer,
	ai.type,
	ai.modifiedAt,
	ai.createdAt
FROM
	Ai ai
WHERE
	{search}