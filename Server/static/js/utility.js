// this is a package of functions used for data manipulation

const kmeans = require('./kmeans')
// given result as a set of loc_ref_points rows, returns data in geoJSON format
const convertLocations = (results) => {
    geoJSON = {
        "type" : "FeatureCollection",
        "features" : []
    }
    const rLength = results.rows.length
    for(let i = 0; i < rLength; i++) {
        row = results.rows[i] //
        feature = {
            "type": "Feature",
            "properties": {
                "id": row.id,
                "db": row.db,
                "neighbour": row.neighbour,
                "range": row.range
            },
            "geometry": {
                "type": "Point",
                "coordinates": [row.st_y, row.st_x] // inverted respect postGIS data

            }

        }

        geoJSON.features.push(feature)
    }
    return geoJSON
}

// convert clusters dataset into a geoJSON data format
// it's similar to the locations but there are like centroids
// and clusters as added attributes in circle geometry format
const convertClusters = (results, dataset) => {
    geoJSON = {
        "type" : "ClustersCollection",
        "locations" : [],
        "centroids" : [],
        "clusters" : [],
    }

    // adding locations features as geometry points for geoJSON view
    const rLength = results.rows.length
    clustsLength = dataset.clusters.length
    for(let i = 0; i < rLength; i++){
        // loop of rows
        cluster_id = 0
        row = results.rows[i] //
        for(let j = 0; j < clustsLength; j++){
            // loop on clusters
            let locations = dataset.clusters[j].locations
            let locLength = locations.length
            for(let l = 0; l < locLength; l++ ){
                // loop of locations of cluster j
                if(locations[l].st_x == row.st_x && locations[l].st_y == row.st_y){
                    // location inside the cluster
                    cluster_id = dataset.clusters[j].cluster_id
                }
            }
        }
        feature = {
            "type" : "Feature",
            "properties" : {
                "id" : row.id,
                "db" : row.db,
                "neighbour" : row.neighbour,
                "range" : row.range,
                "cluster_id" : cluster_id
            },
            "geometry" : {
                "type" : "Point",
                "coordinates" : [row.st_x, row.st_y] // inverted respect postGIS data
            }
        }

        geoJSON.locations.push(feature)
    }

    // adding centroids features as geometry colored points

    for(let i = 0; i < dataset.centroids.length; i++){
        geoJSON.centroids.push(dataset.centroids[i])
        geoJSON.centroids[i].cluster_id = i
    }

    // adding clusters as collection of polygon

    dbMean = calculateMeanDB(dataset)

    for(let i = 0; i < geoJSON.centroids.length; i++){
        // adding a number of clusters as well as centroids numbers
        cluster = {
            "type" : "Cluster",
            "properties" : {
                "cluster_id" : i,
                "db_mean" : dbMean[i],
            },
            "geometry" : {
                "type" : "Polygon",
                "coordinates" : [] // inverted respect postGIS data
            }
        }

        // adding locations coordinates in the coordinates attribute of the Polygon in geoJSON
        locations = dataset.locations
        for(let i = 0; i < locations.length; i++){
            if(locations[i].cluster_id == i){
                // location inside the cluster i, need to be considered
                coord = [ locations[i].st_y, locations[i].st_x]
                console.log(coord)
                cluster.geometry.coordinates.push(coord) // pushing coordinates in the polygon attribute
                console.log("Single cluster adding length:")
                console.log(cluster.geometry.coordinates.length)
            }
        }
        geoJSON.clusters.push(cluster)
        console.log("geoJSON clusters length:")
        console.log(geoJSON.clusters)
    }

    return geoJSON

}


const calculateMeanDB = (dataset) => {
    meanDb = []
    countLoc = []
    locations = dataset.locations
    for(let x = 0; x < dataset.clusters.length; x++){
        meanDb.push(0.0) // init meanDb array
        countLoc.push(0)
    }

    // adding in the meanDb[j] the sum of locations with cluster_id = j
    for(let i = 0; i < locations.length; i++){
        for(let j = 0; j < locations.length; j++){
            if(locations[i].cluster_id == j){
                meanDb[j] = meanDb[j] + locations[i].db
                countLoc[j] = countLoc[j] + 1 // counting of locations of cluster j
            }
        }
    }

    // set means
    for(let i = 0; i < meanDb.length; i++){
        meanDb[i] = meanDb[i] / countLoc[i]
    }

    // return array of meanDb where meanDb[i] is the mean db of the cluster i
    return meanDb

}

// populate DB with dummy points in Bologna area
// range are 0 and 3000 with step 1
// DBs are between 20 to 50
const populateDB = (pool, n) => {
    for(let i = 0; i < n; i++){
        range = Math.floor(Math.random() * (3000 - 0) + 0)
        neighbour = Math.floor(Math.random() * (4000 - 0) + 0)
        db = Math.floor(Math.random() * (55.0 - 20.0) + 20.0)
        long = Math.random() * (44.5226164783769 - 44.428249953517265) + 44.428249953517265;
        lat = Math.random() * (11.42506902374428 - 11.280186829752083) + 11.280186829752083
        pool.query('INSERT INTO public.loc_ref_points(neighbour, range, db, coordinates)VALUES ($1, $2, $3, ST_Point($4, $5));', [neighbour, range, db, long, lat], (error, results) => {
            if (error) {
                throw error
            }
        })


    }
}

module.exports = {
    convertLocations,
    convertClusters,
    populateDB,
    calculateMeanDB,
}