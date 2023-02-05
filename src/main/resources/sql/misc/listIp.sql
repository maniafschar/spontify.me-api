SELECT
	ip.ip,
	ip.hostname,
	ip.org,
	ip.timezone,
	ip.postal,
	ip.country,
	ip.region,
	ip.city,
	ip.latitude,
	ip.longitude
FROM
	Ip ip
WHERE
	{search}