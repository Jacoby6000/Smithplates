# petstore_client.DefaultApi

All URIs are relative to *http://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**create_pet**](DefaultApi.md#create_pet) | **POST** /pets | 
[**delete_pet**](DefaultApi.md#delete_pet) | **DELETE** /pets/{petId} | 
[**get_category**](DefaultApi.md#get_category) | **GET** /categories/{categoryId} | 
[**get_order**](DefaultApi.md#get_order) | **GET** /orders/{orderId} | 
[**get_pet**](DefaultApi.md#get_pet) | **GET** /pets/{petId} | 
[**health_check**](DefaultApi.md#health_check) | **GET** /health | 
[**place_order**](DefaultApi.md#place_order) | **POST** /orders | 
[**resolve_pet_location**](DefaultApi.md#resolve_pet_location) | **GET** /pets/{petId}/location | 
[**update_pet**](DefaultApi.md#update_pet) | **PUT** /pets/{petId} | 


# **create_pet**
> CreatePetResponseContent create_pet(create_pet_request_content)

HTTP-facing petstore contract. Generated FastAPI routes call protocol implementations
in `src/server` that delegate to generated `@sqlService` repositories.

### Example


```python
import petstore_client
from petstore_client.models.create_pet_request_content import CreatePetRequestContent
from petstore_client.models.create_pet_response_content import CreatePetResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    create_pet_request_content = petstore_client.CreatePetRequestContent() # CreatePetRequestContent | 

    try:
        api_response = await api_instance.create_pet(create_pet_request_content)
        print("The response of DefaultApi->create_pet:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->create_pet: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **create_pet_request_content** | [**CreatePetRequestContent**](CreatePetRequestContent.md)|  | 

### Return type

[**CreatePetResponseContent**](CreatePetResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**201** | CreatePet 201 response |  * ETag -  <br>  |
**400** | ValidationError 400 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **delete_pet**
> delete_pet(pet_id)

### Example


```python
import petstore_client
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    pet_id = 'pet_id_example' # str | 

    try:
        await api_instance.delete_pet(pet_id)
    except Exception as e:
        print("Exception when calling DefaultApi->delete_pet: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **pet_id** | **str**|  | 

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**204** | DeletePet 204 response |  -  |
**404** | PetNotFound 404 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **get_category**
> GetCategoryResponseContent get_category(category_id)

### Example


```python
import petstore_client
from petstore_client.models.get_category_response_content import GetCategoryResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    category_id = 'category_id_example' # str | 

    try:
        api_response = await api_instance.get_category(category_id)
        print("The response of DefaultApi->get_category:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->get_category: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **category_id** | **str**|  | 

### Return type

[**GetCategoryResponseContent**](GetCategoryResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | GetCategory 200 response |  -  |
**404** | CategoryNotFound 404 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **get_order**
> GetOrderResponseContent get_order(order_id)

### Example


```python
import petstore_client
from petstore_client.models.get_order_response_content import GetOrderResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    order_id = 'order_id_example' # str | 

    try:
        api_response = await api_instance.get_order(order_id)
        print("The response of DefaultApi->get_order:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->get_order: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **order_id** | **str**|  | 

### Return type

[**GetOrderResponseContent**](GetOrderResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | GetOrder 200 response |  -  |
**404** | OrderNotFound 404 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **get_pet**
> GetPetResponseContent get_pet(pet_id)

### Example


```python
import petstore_client
from petstore_client.models.get_pet_response_content import GetPetResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    pet_id = 'pet_id_example' # str | 

    try:
        api_response = await api_instance.get_pet(pet_id)
        print("The response of DefaultApi->get_pet:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->get_pet: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **pet_id** | **str**|  | 

### Return type

[**GetPetResponseContent**](GetPetResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | GetPet 200 response |  -  |
**404** | PetNotFound 404 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **health_check**
> HealthCheckResponseContent health_check()

### Example


```python
import petstore_client
from petstore_client.models.health_check_response_content import HealthCheckResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)

    try:
        api_response = await api_instance.health_check()
        print("The response of DefaultApi->health_check:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->health_check: %s\n" % e)
```



### Parameters

This endpoint does not need any parameter.

### Return type

[**HealthCheckResponseContent**](HealthCheckResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | HealthCheck 200 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **place_order**
> PlaceOrderResponseContent place_order(place_order_request_content)

### Example


```python
import petstore_client
from petstore_client.models.place_order_request_content import PlaceOrderRequestContent
from petstore_client.models.place_order_response_content import PlaceOrderResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    place_order_request_content = petstore_client.PlaceOrderRequestContent() # PlaceOrderRequestContent | 

    try:
        api_response = await api_instance.place_order(place_order_request_content)
        print("The response of DefaultApi->place_order:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->place_order: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **place_order_request_content** | [**PlaceOrderRequestContent**](PlaceOrderRequestContent.md)|  | 

### Return type

[**PlaceOrderResponseContent**](PlaceOrderResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**201** | PlaceOrder 201 response |  -  |
**400** | ValidationError 400 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **resolve_pet_location**
> resolve_pet_location(pet_id)

### Example


```python
import petstore_client
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    pet_id = 'pet_id_example' # str | 

    try:
        await api_instance.resolve_pet_location(pet_id)
    except Exception as e:
        print("Exception when calling DefaultApi->resolve_pet_location: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **pet_id** | **str**|  | 

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**302** | ResolvePetLocation 302 response |  * Location -  <br>  |
**404** | PetNotFound 404 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **update_pet**
> UpdatePetResponseContent update_pet(pet_id, update_pet_body)

### Example


```python
import petstore_client
from petstore_client.models.update_pet_body import UpdatePetBody
from petstore_client.models.update_pet_response_content import UpdatePetResponseContent
from petstore_client.rest import ApiException
from pprint import pprint

# Defining the host is optional and defaults to http://localhost
# See configuration.py for a list of all supported configuration parameters.
configuration = petstore_client.Configuration(
    host = "http://localhost"
)


# Enter a context with an instance of the API client
async with petstore_client.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = petstore_client.DefaultApi(api_client)
    pet_id = 'pet_id_example' # str | 
    update_pet_body = petstore_client.UpdatePetBody() # UpdatePetBody | 

    try:
        api_response = await api_instance.update_pet(pet_id, update_pet_body)
        print("The response of DefaultApi->update_pet:\n")
        pprint(api_response)
    except Exception as e:
        print("Exception when calling DefaultApi->update_pet: %s\n" % e)
```



### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **pet_id** | **str**|  | 
 **update_pet_body** | [**UpdatePetBody**](UpdatePetBody.md)|  | 

### Return type

[**UpdatePetResponseContent**](UpdatePetResponseContent.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
|-------------|-------------|------------------|
**200** | UpdatePet 200 response |  -  |
**400** | ValidationError 400 response |  -  |
**404** | PetNotFound 404 response |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

