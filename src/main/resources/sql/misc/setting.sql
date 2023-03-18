SELECT
	setting.id,
	setting.label,
	setting.data,
	setting.createdAt,
	setting.modifiedAt
FROM
	Setting setting
WHERE
	{search}
ORDER BY
	setting.id DESC