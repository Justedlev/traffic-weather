package org.traffic.weather.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.traffic.weather.api.ApiConstants;
import org.traffic.weather.api.CodeWithReturnedData;
import org.traffic.weather.api.Codes;
import org.traffic.weather.api.dto.traffic_device_weather_dto.TrafficDeviceDTO;
import org.traffic.weather.api.dto.traffic_device_weather_dto.TrafficWeatherDTO;
import org.traffic.weather.api.dto.weather_dto.WeatherDTO;
import org.traffic.weather.domain.dao.TrafficWeatherRepository;
import org.traffic.weather.domain.entities.TrafficDeviceEntity;
import org.traffic.weather.service.interfaces.ITrafficWeather;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Qualifier("service1")
@Slf4j
public class TrafficWeatherService implements ITrafficWeather {

    @Autowired
    private TrafficWeatherRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper mapper;

    @Override
    public List<TrafficDeviceDTO> getAllTrafficDevices() {
        List<TrafficDeviceDTO> devices = repository.findAll().stream()
                .map(this::convertTrafficDeviceEntityToTrafficDeviceDTO)
                .collect(Collectors.toList());
        log.debug("getting all data from db");
        return devices;
    }

    @Override
    public CodeWithReturnedData<TrafficWeatherDTO> canBeRepaired(String id) {
        TrafficDeviceEntity trafficDevice = repository.findById(id);
        if (trafficDevice == null) {
            log.debug("no data received from db");
            return new CodeWithReturnedData<>(Codes.TRAFFIC_DEVICE_NOT_FOUND, null);
        }
        log.debug("getting data from db, data = {}", trafficDevice);
        return getTrafficWeather(trafficDevice);
    }

    private CodeWithReturnedData<TrafficWeatherDTO> getTrafficWeather(TrafficDeviceEntity trafficDeviceEntity) {
        URI api = getWeatherApiURI(trafficDeviceEntity.getDeviceLongitude(), trafficDeviceEntity.getDeviceLatitude());
        ResponseEntity<String> response = restTemplate.getForEntity(api, String.class);
        log.debug("getting data from weather api = {}, data = {}", api, response.getBody());
        TrafficDeviceDTO trafficDevice = convertTrafficDeviceEntityToTrafficDeviceDTO(trafficDeviceEntity);
        WeatherDTO weatherData;
        try {
            weatherData = mapper.readValue(response.getBody(), WeatherDTO.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.debug("conversion data from weather api '{}' error", api);
            return new CodeWithReturnedData<>(Codes.OK, new TrafficWeatherDTO(trafficDevice, null));
        }
        TrafficWeatherDTO trafficWeatherDTO = new TrafficWeatherDTO(trafficDevice, weatherData);
        return new CodeWithReturnedData<>(Codes.OK, trafficWeatherDTO);
    }

    private URI getWeatherApiURI(double longitude, double latitude) {
        return UriComponentsBuilder.fromHttpUrl(ApiConstants.WEATHER_API_URL)
                .path(ApiConstants.WEATHER_API_PATH)
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("appid", ApiConstants.WEATHER_API_KEY)
                .queryParam("units","metric")
                .build().toUri();
    }

    private TrafficDeviceDTO convertTrafficDeviceEntityToTrafficDeviceDTO(TrafficDeviceEntity entity) {
        return new TrafficDeviceDTO(entity.getId(),
                entity.getLastHeartbeat(),
                entity.getDeviceLongitude(),
                entity.getDeviceLatitude(),
                entity.getDeviceHeight(),
                entity.isEnabled(),
                entity.isConnected());
    }

}
