package com.jq.findapp.repository;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.geo.Point;

public class GeoLocationProcessor {
	private double radLat;
	private double radLon;

	private boolean integer;
	private boolean sort;
	private String table;

	private static final double MIN_LAT = Math.toRadians(-90d);
	private static final double MAX_LAT = Math.toRadians(90d);
	private static final double MIN_LON = Math.toRadians(-180d);
	private static final double MAX_LON = Math.toRadians(180d);
	private static final double radius = 6371.01;

	private GeoLocationProcessor() {
		super();
	}

	public GeoLocationProcessor(QueryParams params) {
		if (params.getLatitude() != null && params.getLongitude() != null) {
			if (params.getDistance() == null)
				params.setDistance(100);
			table = params.getQuery().name().split("_")[0];
			if ("event".equals(table))
				table = "location";
			integer = params.getQuery().name().startsWith("contact_");
			sort = params.isSort();
			radLat = Math.toRadians(params.getLatitude());
			radLon = Math.toRadians(params.getLongitude());
			checkBounds(radLat, radLon);
			final Point[] boundingCoordinates = computeBoundingCoordinates(params.getDistance());
			final boolean meridian180WithinDistance = boundingCoordinates[0].getY() > boundingCoordinates[1].getY();
			params.setSearchGeoLocation(
					"((" + table + ".latitude >= " + boundingCoordinates[0].getX() + " and " + table + ".latitude <= "
							+ boundingCoordinates[1].getX() + ") and (" + table + ".longitude >= "
							+ boundingCoordinates[0].getY() + ' ' + (meridian180WithinDistance ? "or" : "and") + ' '
							+ table + ".longitude <= " + boundingCoordinates[1].getY() + ") and " + "acos(sin("
							+ radLat + ") * sin(radians(" + table + ".latitude)) + cos(" + radLat
							+ ") * cos(radians(" + table + ".latitude)) * cos(radians(" + table + ".longitude) - "
							+ radLon + ")) <= " + (params.getDistance() / radius) + ')');
			if (params.getDistance() == null || params.getDistance() > 50000 || params.getDistance() < 1)
				params.setSearchGeoLocation(
						'(' + table + ".latitude is null or " + params.getSearchGeoLocation() + ')');
		} else
			params.setSearchGeoLocation(null);
	}

	public List<Object[]> postProcessor(List<Object[]> list) {
		if (list.size() > 0) {
			final String[] header = (String[]) list.get(0);
			int distance = -1, latitude = -1, longitude = -1;
			for (int i = 0; i < header.length; i++) {
				if ("_geolocationDistance".equals(header[i]))
					distance = i;
				else if ((table + ".latitude").equals(header[i]))
					latitude = i;
				else if ((table + ".longitude").equals(header[i]))
					longitude = i;
			}
			if (distance > -1 && latitude > -1 && longitude > -1) {
				Object[] o2;
				final Double max = Double.valueOf(Double.MAX_VALUE);
				for (int i = 1; i < list.size(); i++) {
					o2 = list.get(i);
					if (o2[latitude] != null && o2[longitude] != null)
						o2[distance] = Double.valueOf(distanceTo(((Number) o2[latitude]).doubleValue(),
								((Number) o2[longitude]).doubleValue()));
					else
						o2[distance] = max;
				}
				if (sort) {
					final int d = distance;
					Collections.sort(list, new Comparator<Object>() {
						@Override
						public int compare(Object o1, Object o2) {
							if (((Object[]) o1)[d] instanceof Number && ((Object[]) o2)[d] instanceof Number) {
								return (int) (((Number) ((Object[]) o1)[d]).doubleValue() * 1000
										- ((Number) ((Object[]) o2)[d]).doubleValue() * 1000);
							}
							return 1;
						}
					});
				}
				for (int i = 1; i < list.size(); i++) {
					final Object[] row = ((Object[]) list.get(i));
					if (row[distance] == max)
						row[distance] = null;
					else if (integer) {
						if (((Number) row[distance]).doubleValue() <= 1.5)
							row[distance] = 1;
						else
							row[distance] = (int) (((Number) row[distance]).doubleValue() + 0.5);
					}
				}
			}
		}
		return list;
	}

	private void checkBounds(double radLat, double radLon) {
		if (radLat < MIN_LAT || radLat > MAX_LAT || radLon < MIN_LON || radLon > MAX_LON)
			throw new IllegalArgumentException("out of bounds: " + radLat + "/" + radLon);
	}

	private double distanceTo(double latitude, double longitude) {
		final double d = Math.toRadians(latitude);
		return Math.acos(Math.sin(radLat) * Math.sin(d)
				+ Math.cos(radLat) * Math.cos(d) * Math.cos(radLon - Math.toRadians(longitude))) * radius;
	}

	public static double distance(double latitude1, double longitude1, double latitude2, double longitude2) {
		final GeoLocationProcessor geoLocationProcessor = new GeoLocationProcessor();
		geoLocationProcessor.radLat = Math.toRadians(latitude1);
		geoLocationProcessor.radLon = Math.toRadians(longitude1);
		return geoLocationProcessor.distanceTo(latitude2, longitude2);
	}

	/**
	 * <p>
	 * Computes the bounding coordinates of all points on the surface of a sphere
	 * that have a great circle distance to the point represented by this
	 * GeoLocation instance that is less or equal to the distance argument.
	 * </p>
	 * <p>
	 * For more information about the formulae used in this method visit
	 * <a href="http://JanMatuschek.de/LatitudeLongitudeBoundingCoordinates">
	 * http://JanMatuschek.de/LatitudeLongitudeBoundingCoordinates</a>.
	 * </p>
	 * 
	 * @param distance the distance from the point represented by this GeoLocation
	 *                 instance. Must me measured in the same unit as the radius
	 *                 argument.
	 * @param radius   the radius of the sphere, e.g. the average radius for a
	 *                 spherical approximation of the figure of the Earth is
	 *                 approximately 6371.01 kilometers.
	 * @return an array of two GeoLocation objects such that:
	 *         <ul>
	 *         <li>The latitude of any point within the specified distance is
	 *         greater or equal to the latitude of the first array element and
	 *         smaller or equal to the latitude of the second array element.</li>
	 *         <li>If the longitude of the first array element is smaller or equal
	 *         to the longitude of the second element, then the longitude of any
	 *         point within the specified distance is greater or equal to the
	 *         longitude of the first array element and smaller or equal to the
	 *         longitude of the second array element.</li>
	 *         <li>If the longitude of the first array element is greater than the
	 *         longitude of the second element (this is the case if the 180th
	 *         meridian is within the distance), then the longitude of any point
	 *         within the specified distance is greater or equal to the longitude of
	 *         the first array element <strong>or</strong> smaller or equal to the
	 *         longitude of the second array element.</li>
	 *         </ul>
	 */
	private Point[] computeBoundingCoordinates(double distance) {
		if (distance < 0d)
			throw new IllegalArgumentException("distance negative: " + distance);

		// angular distance in radians on a great circle
		double radDist = distance / radius;
		double minLat = radLat - radDist;
		double maxLat = radLat + radDist;
		double minLon, maxLon;
		if (minLat > MIN_LAT && maxLat < MAX_LAT) {
			double deltaLon = Math.asin(Math.sin(radDist) / Math.cos(radLat));
			minLon = radLon - deltaLon;
			if (minLon < MIN_LON)
				minLon += 2d * Math.PI;
			maxLon = radLon + deltaLon;
			if (maxLon > MAX_LON)
				maxLon -= 2d * Math.PI;
		} else {
			// a pole is within the distance
			minLat = Math.max(minLat, MIN_LAT);
			maxLat = Math.min(maxLat, MAX_LAT);
			minLon = MIN_LON;
			maxLon = MAX_LON;
		}
		checkBounds(minLat, minLon);
		checkBounds(maxLat, maxLon);
		return new Point[] { new Point(Math.toDegrees(minLat), Math.toDegrees(minLon)),
				new Point(Math.toDegrees(maxLat), Math.toDegrees(maxLon)) };
	}
}