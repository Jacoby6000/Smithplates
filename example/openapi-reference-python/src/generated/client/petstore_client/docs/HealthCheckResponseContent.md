# HealthCheckResponseContent


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**status** | **str** |  | 

## Example

```python
from petstore_client.models.health_check_response_content import HealthCheckResponseContent

# TODO update the JSON string below
json = "{}"
# create an instance of HealthCheckResponseContent from a JSON string
health_check_response_content_instance = HealthCheckResponseContent.from_json(json)
# print the JSON string representation of the object
print(HealthCheckResponseContent.to_json())

# convert the object into a dict
health_check_response_content_dict = health_check_response_content_instance.to_dict()
# create an instance of HealthCheckResponseContent from a dict
health_check_response_content_from_dict = HealthCheckResponseContent.from_dict(health_check_response_content_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


