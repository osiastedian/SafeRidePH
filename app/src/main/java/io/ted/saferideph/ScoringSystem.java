package io.ted.saferideph;

public class ScoringSystem {

    class Place {
        final static String accounting = "accounting";
        final static String airport = "airport";
        final static String amusement_park = "amusement_park";
        final static String aquarium = "aquarium";
        final static String art_gallery = "art_gallery";
        final static String atm = "atm";
        final static String bakery = "bakery";
        final static String bank = "bank";
        final static String bar = "bar";
        final static String beauty_salon = "beauty_salon";
        final static String bicycle_store = "bicycle_store";
        final static String book_store = "book_store";
        final static String bowling_alley = "bowling_alley";
        final static String bus_station = "bus_station";
        final static String cafe = "cafe";
        final static String campground = "campground";
        final static String car_dealer = "car_dealer";
        final static String car_rental = "car_rental";
        final static String car_repair = "car_repair";
        final static String car_wash = "car_wash";
        final static String casino = "casino";
        final static String cemetery = "cemetery";
        final static String church = "church";
        final static String city_hall = "city_hall";
        final static String clothing_store = "clothing_store";
        final static String convenience_store = "convenience_store";
        final static String courthouse = "courthouse";
        final static String dentist = "dentist";
        final static String department_store = "department_store";
        final static String doctor = "doctor";
        final static String electrician = "electrician";
        final static String electronics_store = "electronics_store";
        final static String embassy = "embassy";
        final static String fire_station = "fire_station";
        final static String florist = "florist";
        final static String funeral_home = "funeral_home";
        final static String furniture_store = "furniture_store";
        final static String gas_station = "gas_station";
        final static String gym = "gym";
        final static String hair_care = "hair_care";
        final static String hardware_store = "hardware_store";
        final static String hindu_temple = "hindu_temple";
        final static String home_goods_store = "home_goods_store";
        final static String hospital = "hospital";
        final static String insurance_agency = "insurance_agency";
        final static String jewelry_store = "jewelry_store";
        final static String laundry = "laundry";
        final static String lawyer = "lawyer";
        final static String library = "library";
        final static String liquor_store = "liquor_store";
        final static String local_government_office = "local_government_office";
        final static String locksmith = "locksmith";
        final static String lodging = "lodging";
        final static String meal_delivery = "meal_delivery";
        final static String meal_takeaway = "meal_takeaway";
        final static String mosque = "mosque";
        final static String movie_rental = "movie_rental";
        final static String movie_theater = "movie_theater";
        final static String moving_company = "moving_company";
        final static String museum = "museum";
        final static String night_club = "night_club";
        final static String painter = "painter";
        final static String park = "park";
        final static String parking = "parking";
        final static String pet_store = "pet_store";
        final static String pharmacy = "pharmacy";
        final static String physiotherapist = "physiotherapist";
        final static String plumber = "plumber";
        final static String police = "police";
        final static String post_office = "post_office";
        final static String real_estate_agency = "real_estate_agency";
        final static String restaurant = "restaurant";
        final static String roofing_contractor = "roofing_contractor";
        final static String rv_park = "rv_park";
        final static String school = "school";
        final static String shoe_store = "shoe_store";
        final static String shopping_mall = "shopping_mall";
        final static String spa = "spa";
        final static String stadium = "stadium";
        final static String storage = "storage";
        final static String store = "store";
        final static String subway_station = "subway_station";
        final static String supermarket = "supermarket";
        final static String synagogue = "synagogue";
        final static String taxi_stand = "taxi_stand";
        final static String train_station = "train_station";
        final static String transit_station = "transit_station";
        final static String travel_agency = "travel_agency";
        final static String veterinary_care = "veterinary_care";
        final static String zoo = "zoo";
    }

    public static long getScore(NearbyPlace place) {
        if(place == null) return -1;
        long score = 0;
        String []types = place.getTypes();
        for (String type :
                types) {
            score += getPlaceScore(type);
        }
        return score;
    }

    public static String getHighscorePlaceType(NearbyPlace place) {
        if(place == null) return  null;
        String[] types = place.getTypes();
        if(types == null || types.length == 0) return null;
        String highestType = types[0];
        long highestScore = getPlaceScore(highestType);
        for (String type :
                types) {
            long score = getPlaceScore(type);
            if(highestScore < score) {
                highestType = type;
                highestScore = score;
            }
        }
        return highestType;
    }

    private static long getPlaceScore(String place) {
        switch (place) {
            case Place.airport: return 5;
            case Place.amusement_park: return 5;
            case Place.bus_station: return 5;
            case Place.church: return 5;
            case Place.city_hall: return 5;
            case Place.department_store: return 5;
            case Place.hindu_temple: return 5;
            case Place.home_goods_store: return 5;
            case Place.hospital: return 5;
            case Place.library: return 5;
            case Place.local_government_office: return 5;
            case Place.mosque: return 5;
            case Place.movie_theater: return 5;
            case Place.museum: return 5;
            case Place.night_club: return 5;
            case Place.park: return 5;
            case Place.school: return 5;
            case Place.shopping_mall: return 5;
            case Place.stadium: return 5;
            case Place.store: return 5;
            case Place.supermarket: return 5;
            case Place.train_station: return 5;
            case Place.transit_station: return 5;
            case Place.zoo: return 5;
            case Place.aquarium: return 3;
            case Place.art_gallery: return 3;
            case Place.bank: return 3;
            case Place.bar: return 3;
            case Place.bicycle_store: return 3;
            case Place.book_store: return 3;
            case Place.bowling_alley: return 3;
            case Place.car_dealer: return 3;
            case Place.car_rental: return 3;
            case Place.car_repair: return 3;
            case Place.car_wash: return 3;
            case Place.casino: return 3;
            case Place.cemetery: return 3;
            case Place.clothing_store: return 3;
            case Place.convenience_store: return 3;
            case Place.courthouse: return 3;
            case Place.electronics_store: return 3;
            case Place.embassy: return 3;
            case Place.fire_station: return 3;
            case Place.funeral_home: return 3;
            case Place.furniture_store: return 3;
            case Place.gas_station: return 3;
            case Place.hair_care: return 3;
            case Place.hardware_store: return 3;
            case Place.insurance_agency: return 3;
            case Place.jewelry_store: return 3;
            case Place.liquor_store: return 3;
            case Place.lodging: return 3;
            case Place.meal_takeaway: return 3;
            case Place.movie_rental: return 3;
            case Place.parking: return 3;
            case Place.pet_store: return 3;
            case Place.pharmacy: return 3;
            case Place.police: return 3;
            case Place.post_office: return 3;
            case Place.real_estate_agency: return 3;
            case Place.restaurant: return 3;
            case Place.rv_park: return 3;
            case Place.shoe_store: return 3;
            case Place.spa: return 3;
            case Place.synagogue: return 3;
            case Place.veterinary_care: return 3;
            case Place.accounting: return 1;
            case Place.atm: return 1;
            case Place.bakery: return 1;
            case Place.beauty_salon: return 1;
            case Place.cafe: return 1;
            case Place.campground: return 1;
            case Place.dentist: return 1;
            case Place.doctor: return 1;
            case Place.electrician: return 1;
            case Place.florist: return 1;
            case Place.gym: return 1;
            case Place.laundry: return 1;
            case Place.lawyer: return 1;
            case Place.locksmith: return 1;
            case Place.meal_delivery: return 1;
            case Place.moving_company: return 1;
            case Place.painter: return 1;
            case Place.physiotherapist: return 1;
            case Place.plumber: return 1;
            case Place.roofing_contractor: return 1;
            case Place.storage: return 1;
            case Place.subway_station: return 1;
            case Place.taxi_stand: return 1;
            case Place.travel_agency: return 1;
            default: return 0;
        }
    }

}
